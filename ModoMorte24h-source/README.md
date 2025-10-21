ModoMorte24h - Plugin source for Paper 1.21.x
=============================================

Descrição
---------
Plugin que coloca jogadores mortos no modo espectador por 24 horas. Se não houver jogadores vivos online,
o jogador dentro do período de penalidade não consegue logar até que haja um jogador vivo.

Como compilar (Maven)
---------------------
1. Tenha Java 17+ e Maven instalados.
2. No diretório do projeto (onde está o pom.xml), execute:
   mvn clean package

3. O JAR estará em:
   target/ModoMorte24h-1.0.jar

Notas
-----
- O dependency 'paper-api' está marcado como 'provided' (o servidor fornece a API em runtime).
- Este projeto foi configurado para ser compatível com Paper 1.21.x.
- Se quiser, você pode usar GitHub Actions ou um servidor CI para compilar automaticamente e gerar o .jar.

Uso
---
Coloque o JAR gerado em plugins/ do servidor Paper 1.21.x e reinicie.
