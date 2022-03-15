#!/usr/bin/env python3
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

import sys

# content of test_example.py
import unittest

import pytest


# unit test in a pytest
class TestRuleKeyDiff(unittest.TestCase):
    def test_key_value_diff(self):
        pass


@pytest.fixture
def error_fixture():
    assert 0


def test_ok():
    print("ok")


@pytest.mark.xfail
def test_fail_with_std():
    print("stdout print")
    print("stderr print", file=sys.stderr)
    assert 0
