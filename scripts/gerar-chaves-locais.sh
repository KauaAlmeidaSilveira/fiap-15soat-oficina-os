#!/bin/sh
# Gera, em .local/chaves (gitignorado), os pares usados pelo docker-compose e pelo BDD.
set -e
cd "$(dirname "$0")/.."
mkdir -p .local/chaves
for par in sessao servico aprovacao; do
    if [ ! -f ".local/chaves/$par.private.key" ]; then
        openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out ".local/chaves/$par.private.key" 2>/dev/null
        openssl rsa -in ".local/chaves/$par.private.key" -pubout -out ".local/chaves/$par.public.key" 2>/dev/null
    fi
done
