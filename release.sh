#!/bin/bash

# XSprint Git Release Script
VERSION="26.10.25.1"
DESCRIPTION="$1"

if [ -z "$DESCRIPTION" ]; then
    echo "Usage: ./release.sh <commit_description>"
    echo "Example: ./release.sh \"Initial release\""
    exit 1
fi

echo "=== XSprint v${VERSION} Release ==="

# 1. 更新版本号到 pom.xml
sed -i "s/<version>.*<\/version>/<version>${VERSION}<\/version>/" pom.xml
sed -i "s/<VERSION>.*<\/VERSION>/<VERSION>${VERSION}<\/VERSION>/" pom.xml

# 2. 更新版本号到 plugin.yml
sed -i "s/version: .*/version: ${VERSION}/" src/main/resources/plugin.yml

# 3. 更新版本号到 build.sh
sed -i "s/VERSION=.*/VERSION=\"${VERSION}\"/" build.sh

# 4. 构建
echo "Building..."
chmod +x build.sh
./build.sh

# 5. Git 操作
git add -A
git commit -m "v${VERSION}: ${DESCRIPTION}"
git tag "v${VERSION}"
git push origin main
git push origin "v${VERSION}"

echo "=== Release Complete ==="
echo "Tag: v${VERSION}"
echo "JAR: build/XSprint-${VERSION}.jar"
