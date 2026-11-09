#!/bin/sh
# Sobe o sistema inteiro, roda os cenários Cucumber e derruba tudo, mesmo se algum falhar.
set -e
cd "$(dirname "$0")/.."
sh scripts/gerar-chaves-locais.sh
trap 'docker compose down -v --remove-orphans' EXIT
docker compose up -d --build --wait
mvn -q -B test -Dtest=BddFluxosSuite -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true
