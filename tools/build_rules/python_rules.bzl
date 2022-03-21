# Copyright (c) Meta Platforms, Inc. and affiliates.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may
# not use this file except in compliance with the License. You may obtain
# a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.

"""Module containing Python macros."""

def interpreter_override_args():
    """Determine interpreter override arguments based on a buck config.

    For example, `--config user.buck_pex_interpreter=python2` will generate a pex
    which invokes `python2` instead of the default, `python<major>.<minor>`.

    Returns:
      a list of arguments to pass to python interpreter.
    """
    val = native.read_config("user", "buck_pex_interpreter", "")
    if val != "":
        return ["--python-shebang", "/usr/bin/env " + val]
    else:
        return []

def pytest_test(name, srcs = None, deps = None, main_module = "tools.pytest.pytest_runner", package_style = "inplace", env = None, **kwargs):
    """A pytest test"""

    if native.host_info().os.is_windows:
        return

    srcs = srcs or []
    deps = deps or []

    # pytest discovery requires specific file names (https://fburl.com/65on3lox)
    # Tests will be ignored if file names do not match these
    for src in srcs:
        final_path = src.split("/")[-1].strip()

        # src is a file
        if not (final_path.startswith("test_") or final_path.endswith("_test.py")):
            fail("src files must be named `test_*.py` or `*_test.py` for pytest: {}".format(src))

    deps.extend(["//tools/pytest:pytest_runner", "//third-party/py/setuptools:setuptools"])

    env = env or {}
    env["PYTEST_ADDOPTS"] = (
        "-v -p no:cacheprovider --continue-on-collection-errors --color=no"
    )

    if package_style != "inplace":
        # pytest execution requires the original source files and doesn't work with zip imports
        fail("package_style is not allowed to be configured for pytests")

    native.python_test(
        name = name,
        srcs = srcs,
        deps = deps,
        main_module = main_module,
        package_style = package_style,
        env = env,
        **kwargs
    )

def e2e_test(
        name,
        srcs = None,
        deps = None,
        env = None,
        data = None,
        conftest = "fbsource//xplat/build_infra/buck_e2e:conftest.py",
        resources = (),
        **kwargs):
    return
