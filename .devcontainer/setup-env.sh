#!/bin/bash

set -e

# Configure git with custom email and name
git config --global user.email "6574825+rrramiro@users.noreply.github.com"
git config --global user.name "Ramiro Calle"


# Optional: Set additional git configurations
git config --global init.defaultBranch main
git config --global pull.rebase true
git config --global core.autocrlf input

if command -v cs &> /dev/null; then
    echo "✓ Coursier is already installed!"
    cs version
    exit 0
fi

echo "Coursier not found. Installing..."

curl -fL "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux.gz" | gzip -d > coursier
chmod +x coursier
./coursier setup --jvm 17 --apps bloop,metals,ammonite,cs,coursier,scala,scalac,scala-cli,sbt,scalafmt --yes --user-home ~
rm -f coursier
