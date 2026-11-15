#!/bin/sh
# Qualidade de código com SonarQube local (Docker), sem conta nem CI: o GitHub Actions está bloqueado
# por billing, e o enunciado pede validação de qualidade "via SonarQube ou similar". O passo equivalente
# no CI já está no .github/workflows/ci.yml de cada serviço (SonarCloud, ligado pelo secret SONAR_TOKEN).
#
# Sobe o SonarQube Community, cria os projetos, roda `mvn verify sonar:sonar` em cada serviço (testes,
# cobertura do JaCoCo e análise) e espera o quality gate "Sonar way". Roda da pasta com os repositórios
# lado a lado. O painel fica em http://localhost:9000 (usuário admin, senha em .local/sonar-senha-admin).
#
# Uso:
#   sh scripts/sonar-local.sh                 # os 3 serviços
#   sh scripts/sonar-local.sh billing         # só um
#   docker rm -f oficina-sonarqube            # descartar o SonarQube (perde o histórico)
set -eu

OS_DIR=$(cd "$(dirname "$0")/.." && pwd)
RAIZ=$(dirname "$OS_DIR")
CHAVES="$OS_DIR/.local/chaves"
SERVICOS=${*:-"os execucao billing"}
URL=http://localhost:9000
CONTAINER=oficina-sonarqube
IMAGEM=sonarqube:10.7.0-community
# Senha do admin do SonarQube local: gerada no primeiro uso e guardada em .local/ (gitignorado).
SENHA_ARQ="$OS_DIR/.local/sonar-senha-admin"
if [ -z "${SONAR_SENHA_ADMIN:-}" ]; then
  mkdir -p "$OS_DIR/.local"
  [ -s "$SENHA_ARQ" ] || { printf 'Sq-%s' "$(openssl rand -hex 12)" > "$SENHA_ARQ"; chmod 600 "$SENHA_ARQ"; }
  SONAR_SENHA_ADMIN=$(cat "$SENHA_ARQ")
fi
SENHA=$SONAR_SENHA_ADMIN
PLUGIN=org.sonarsource.scanner.maven:sonar-maven-plugin:4.0.0.4121

falha() { echo "ERRO: $*" >&2; exit 1; }
api() { curl -sf -u "admin:$SENHA" "$@"; }

docker info >/dev/null 2>&1 || falha "Docker desligado"
if [ -x /usr/libexec/java_home ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null) || falha "JDK 21 não encontrado"
  export JAVA_HOME
fi

# --- SonarQube ---------------------------------------------------------------------------------
if [ -z "$(docker ps -q -f name="^$CONTAINER$")" ]; then
  if [ -n "$(docker ps -aq -f name="^$CONTAINER$")" ]; then
    docker start "$CONTAINER" >/dev/null
  else
    docker run -d --name "$CONTAINER" -p 9000:9000 "$IMAGEM" >/dev/null
  fi
fi
printf "Esperando o SonarQube"
for _ in $(seq 1 90); do
  [ "$(curl -s "$URL/api/system/status" | sed -n 's/.*"status":"\([A-Z]*\)".*/\1/p')" = UP ] && break
  printf .; sleep 2
done
echo
[ "$(curl -s "$URL/api/system/status" | sed -n 's/.*"status":"\([A-Z]*\)".*/\1/p')" = UP ] || falha "SonarQube não subiu (docker logs $CONTAINER)"

# Primeiro acesso: troca a senha padrão admin/admin, que o SonarQube obriga a mudar.
curl -sf -u admin:admin -X POST "$URL/api/users/change_password" \
  -d login=admin -d previousPassword=admin -d "password=$SENHA" >/dev/null 2>&1 || true
api "$URL/api/system/status" >/dev/null || falha "senha do admin não confere (SONAR_SENHA_ADMIN)"

TOKEN=$(api -X POST "$URL/api/user_tokens/generate" -d "name=oficina-local-$(date +%s)" \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || falha "não foi possível gerar o token de análise"

# --- Análise -----------------------------------------------------------------------------------
RESUMO=""
for s in $SERVICOS; do
  DIR="$RAIZ/fiap-15soat-oficina-$s"
  [ -d "$DIR" ] || falha "repositório não encontrado: $DIR"
  CHAVE="oficina-$s"
  echo "=== $s"
  api -X POST "$URL/api/projects/create" -d "project=$CHAVE" -d "name=fiap-15soat-oficina-$s" >/dev/null 2>&1 || true

  # Os testes leem as chaves de src/main/resources (gitignorado); reaproveita as de .local/chaves.
  RES="$DIR/src/main/resources"
  copiar() { [ -f "$RES/$2" ] || cp "$CHAVES/$1" "$RES/$2"; }
  copiar sessao.public.key jwt.public.key
  case $s in
    os) copiar sessao.private.key jwt.private.key
        copiar servico.private.key servico.private.key
        copiar servico.public.key servico.public.key ;;
    billing) copiar aprovacao.private.key aprovacao.private.key
             copiar aprovacao.public.key aprovacao.public.key ;;
  esac

  # Testes primeiro: se falharem, o resultado é falha de build, nunca o gate de uma análise anterior.
  if ! (cd "$DIR" && mvn -q verify); then
    RESUMO="$RESUMO
$s: FALHOU (build ou testes; nada foi analisado)"
    continue
  fi
  if (cd "$DIR" && mvn -q "$PLUGIN:sonar" \
        -Dsonar.host.url="$URL" -Dsonar.token="$TOKEN" \
        -Dsonar.projectKey="$CHAVE" -Dsonar.projectName="fiap-15soat-oficina-$s" \
        -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300); then
    GATE=OK
  else
    GATE=$(api "$URL/api/qualitygates/project_status?projectKey=$CHAVE" | sed -n 's/.*"projectStatus":{"status":"\([A-Z_]*\)".*/\1/p')
    GATE="${GATE:-FALHOU} (ver $URL/dashboard?id=$CHAVE)"
  fi
  COBERTURA=$(api "$URL/api/measures/component?component=$CHAVE&metricKeys=coverage" | sed -n 's/.*"value":"\([0-9.]*\)".*/\1/p')
  RESUMO="$RESUMO
$s: quality gate $GATE, cobertura ${COBERTURA:-?}% -> $URL/dashboard?id=$CHAVE"
done

echo
echo "Resultado:$RESUMO"
echo "Painel: $URL (usuário admin, senha em $SENHA_ARQ)"
# Sai com erro se algum serviço falhou nos testes ou no quality gate.
if echo "$RESUMO" | grep -v "^$" | grep -qv "quality gate OK"; then
  exit 1
fi
