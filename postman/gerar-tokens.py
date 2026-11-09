#!/usr/bin/env python3
"""Gera o environment local do Postman com tokens assinados pelas chaves de .local/chaves.

Localmente não há Lambda de login, então os tokens saem daqui. Valem 30 dias. Rode de novo depois de
trocar as chaves com scripts/gerar-chaves-locais.sh e importe o environment outra vez.
O environment versionado (sem tokens) é o modelo; o resultado vai para oficina-fase4.environment.local.json,
ignorado pelo git.
"""
import base64
import json
import subprocess
import time
import uuid
from pathlib import Path

PASTA = Path(__file__).resolve().parent
CHAVES = PASTA.parent / ".local" / "chaves"
MODELO = PASTA / "oficina-fase4.environment.json"
ENVIRONMENT = PASTA / "oficina-fase4.environment.local.json"
VALIDADE_SEGUNDOS = 30 * 24 * 3600


def b64(dados: bytes) -> str:
    return base64.urlsafe_b64encode(dados).rstrip(b"=").decode()


def assinar(chave: str, claims: dict) -> str:
    agora = int(time.time())
    corpo = {**claims, "iat": agora, "exp": agora + VALIDADE_SEGUNDOS, "jti": str(uuid.uuid4())}
    cabecalho = {"alg": "RS256", "typ": "JWT"}
    entrada = b64(json.dumps(cabecalho).encode()) + "." + b64(json.dumps(corpo).encode())
    assinatura = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", str(CHAVES / f"{chave}.private.key")],
        input=entrada.encode(), capture_output=True, check=True,
    ).stdout
    return entrada + "." + b64(assinatura)


SESSAO = {"iss": "http://oficina-local", "aud": "oficina-api"}
tokens = {
    "token": assinar("sessao", {**SESSAO, "sub": "postman@oficina.local", "roles": ["ADMIN", "RECEPCAO", "OPERADOR"]}),
    "token_cliente": assinar("sessao", {**SESSAO, "sub": "1", "roles": ["CLIENTE"]}),
    "token_lambda": assinar("sessao", {"iss": "oficina-lambda-auth", "aud": "oficina-os", "sub": "oficina-lambda-auth"}),
    "token_servico": assinar("servico", {"iss": "oficina-os", "aud": "oficina-execucao", "sub": "oficina-os"}),
}

environment = json.loads(MODELO.read_text(encoding="utf-8"))
for variavel in environment["values"]:
    if variavel["key"] in tokens:
        variavel["value"] = tokens[variavel["key"]]
ENVIRONMENT.write_text(json.dumps(environment, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Tokens gravados em {ENVIRONMENT.name}. Importe o arquivo no Postman.")
