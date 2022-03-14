#!/bin/bash
# Copyright (c) Meta Platforms, Inc. and affiliates.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


set -x
set -euo pipefail

SCRIPTS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
BUCK_ROOT="$(dirname "$SCRIPTS_DIR")"
BUCK_FBXCTEST_DIR=$BUCK_ROOT/test/com/facebook/buck/apple/testdata/fbxctest

rm -rf $BUCK_FBXCTEST_DIR/{Frameworks,bin}
mkdir $BUCK_FBXCTEST_DIR/{Frameworks,bin}

TMPDIR=`mktemp -d`
cd $TMPDIR

git clone https://github.com/facebook/idb
pushd idb
./build.sh fbxctest build
cp build/Build/Products/Debug/fbxctest $BUCK_FBXCTEST_DIR/bin

./build.sh framework build
find build/Build/Products/Debug/ -type l -delete
find build/Build/Products/Debug/ -name Frameworks | xargs rm -rf
find build/Build/Products/Debug/ -name Headers | xargs rm -rf
find build/Build/Products/Debug/ -name PrivateHeaders | xargs rm -rf
find build/Build/Products/Debug/ -name Modules | xargs rm -rf
cp -r build/Build/Products/Debug/*.framework $BUCK_FBXCTEST_DIR/Frameworks
cp -r $BUCK_FBXCTEST_DIR/Frameworks/XCTestBootstrap.framework/Versions/A/Resources $BUCK_FBXCTEST_DIR/Frameworks/FBControlCore.framework
popd
