#!/usr/bin/env python3
"""Gera o environment do Postman para o ambiente na AWS, com as URLs apontando para o API Gateway.

Na AWS os tokens vêm da Lambda: rode antes a pasta "Só na AWS: Login", que grava token e token_cliente.
Mailpit, WireMock e as rotas /internal e /api/simulacao não passam pelo Gateway; essas URLs ficam vazias.
O resultado vai para oficina-fase4.environment.aws.local.json, ignorado pelo git (a URL muda a cada subida).

Uso: AWS_PROFILE=personal-tf python3 postman/gerar-environment-aws.py
"""
import json
import subprocess
from pathlib import Path

PASTA = Path(__file__).resolve().parent
LAMBDA_TF = PASTA.parent.parent / "fiap-15soat-oficina-lambda-auth" / "terraform"
MODELO = PASTA / "oficina-fase4.environment.json"
ENVIRONMENT = PASTA / "oficina-fase4.environment.aws.local.json"

url = subprocess.run(
    ["terraform", f"-chdir={LAMBDA_TF}", "output", "-raw", "api_gateway_url"],
    capture_output=True, text=True, check=True,
).stdout.strip().rstrip("/")
if not url.startswith("https://"):
    raise SystemExit(f"api_gateway_url inesperada: {url!r}. O lambda-auth já teve apply?")

valores = {"url_os": url, "url_execucao": url, "url_billing": url, "url_auth": url,
           "url_mercado_pago": "", "url_mailpit": ""}
environment = json.loads(MODELO.read_text(encoding="utf-8"))
environment["id"] = "8b0f6a52-3c1e-4d7a-9f2b-aws000000001"
environment["name"] = "Oficina Fase 4 - AWS"
for variavel in environment["values"]:
    if variavel["key"] in valores:
        variavel["value"] = valores[variavel["key"]]
    elif variavel["key"].startswith("token"):
        variavel["value"] = ""
ENVIRONMENT.write_text(json.dumps(environment, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"{ENVIRONMENT.name}: todas as URLs em {url}")
