#!/usr/bin/env python3
"""B5-0028 (WP-10, A3): validate an effect document against the JSON Schema.

Why this exists
---------------
A3's validation paragraph: "Validate effect data against a JSON Schema in
the dev tooling (Python, already used by the project) at gate time. The
shipped game keeps MiniJson and the stdlib-only rule."

This is the dev-tooling half of that sentence. The shipped-game half is
`b5ccg.engine.effect.EffectLoader`, which enforces the same closed sets at
load time and fails closed. The two are deliberately independent: the schema
is checked by a Python process that does not share a line of code with the
engine, so a mistake in one is not automatically a mistake in the other.

Dependency-free
----------------
The project has no third-party Python dependencies, and the build is
stdlib-only (AGENTS.md section 2). Rather than depend on `jsonschema`, this
implements the subset of JSON Schema draft 2020-12 that the effect schema
actually uses:

    $ref (local, "#/...")   type      enum      const
    required                properties          additionalProperties (false only)
    items                   minItems  minLength minimum   pattern

If the schema starts using a keyword this validator does not implement, the
validator FAILS LOUDLY rather than ignoring it - see `_unsupported`. A
validator that silently skips a keyword it does not understand is a validator
that reports passes it never checked.

Exit codes
----------
    0  the document validates
    1  the document does not validate (one line per violation)
    2  the schema, the document, or the schema file itself could not be read
"""

import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))

DEFAULT_SCHEMA = os.path.join(HERE, "schemas", "effect-document.schema.json")
DEFAULT_DOCUMENT = os.path.join(REPO, "b5ccg", "resources", "effects", "effects-v0.json")

# Keywords this validator implements. Anything else in a schema is a hard
# error: a schema keyword that is silently ignored turns a check into a
# decoration.
SUPPORTED = frozenset([
    "$schema", "$id", "$defs", "$ref", "title", "description",
    "type", "enum", "const",
    "required", "properties", "additionalProperties",
    "items", "minItems", "minLength", "minimum", "pattern",
])


def schema_keywords(node):
    """Every keyword that appears anywhere in the schema, at any depth.

    The keys of a `properties` map and of a `$defs` map are NAMES, not
    keywords - collecting them would report every property name in the
    effect schema as an unimplemented keyword.
    """
    found = set()
    if isinstance(node, dict):
        for key, value in node.items():
            found.add(key)
            if key in ("properties", "$defs"):
                for sub in value.values():
                    found |= schema_keywords(sub)
            else:
                found |= schema_keywords(value)
    elif isinstance(node, list):
        for item in node:
            found |= schema_keywords(item)
    return found


def unsupported(schema):
    """The keywords the schema uses that this validator does not implement."""
    return sorted(schema_keywords(schema) - SUPPORTED)


class Validator(object):
    def __init__(self, schema):
        self.schema = schema
        self.errors = []

    def fail(self, path, message):
        self.errors.append("%s: %s" % (path, message))

    def resolve(self, ref):
        if not ref.startswith("#/"):
            raise ValueError("only local refs are supported, got %r" % ref)
        node = self.schema
        for part in ref[2:].split("/"):
            part = part.replace("~1", "/").replace("~0", "~")
            node = node[part]
        return node

    def validate(self, instance, schema, path):
        if "$ref" in schema:
            self.validate(instance, self.resolve(schema["$ref"]), path)
            return

        if "type" in schema:
            expected = schema["type"]
            if not self.type_ok(instance, expected):
                self.fail(path, "expected type %s, got %s"
                          % (expected, self.type_name(instance)))
                return

        if "const" in schema and instance != schema["const"]:
            self.fail(path, "expected the constant %r, got %r" % (schema["const"], instance))

        if "enum" in schema and instance not in schema["enum"]:
            self.fail(path, "value %r is not one of %s" % (instance, schema["enum"]))

        if isinstance(instance, dict):
            self.validate_object(instance, schema, path)
        elif isinstance(instance, list):
            self.validate_array(instance, schema, path)
        elif isinstance(instance, str):
            self.validate_string(instance, schema, path)
        elif isinstance(instance, bool):
            pass
        elif isinstance(instance, (int, float)):
            self.validate_number(instance, schema, path)

    def type_ok(self, instance, expected):
        if expected == "object":
            return isinstance(instance, dict)
        if expected == "array":
            return isinstance(instance, list)
        if expected == "string":
            return isinstance(instance, str)
        if expected == "integer":
            return isinstance(instance, int) and not isinstance(instance, bool)
        if expected == "number":
            return isinstance(instance, (int, float)) and not isinstance(instance, bool)
        if expected == "boolean":
            return isinstance(instance, bool)
        if expected == "null":
            return instance is None
        raise ValueError("unknown type keyword value %r" % expected)

    @staticmethod
    def type_name(instance):
        if instance is None:
            return "null"
        if isinstance(instance, bool):
            return "boolean"
        if isinstance(instance, int):
            return "integer"
        if isinstance(instance, float):
            return "number"
        if isinstance(instance, str):
            return "string"
        if isinstance(instance, list):
            return "array"
        if isinstance(instance, dict):
            return "object"
        return "unknown"

    def validate_object(self, instance, schema, path):
        for key in schema.get("required", []):
            if key not in instance:
                self.fail(path, "missing required property %r" % key)
        properties = schema.get("properties", {})
        for key in sorted(instance.keys()):
            if key in properties:
                self.validate(instance[key], properties[key], "%s.%s" % (path, key))
            elif schema.get("additionalProperties", True) is False:
                self.fail(path, "unknown property %r" % key)

    def validate_array(self, instance, schema, path):
        if "minItems" in schema and len(instance) < schema["minItems"]:
            self.fail(path, "expected at least %d item(s), got %d"
                      % (schema["minItems"], len(instance)))
        if "items" in schema:
            for index, item in enumerate(instance):
                self.validate(item, schema["items"], "%s[%d]" % (path, index))

    def validate_string(self, instance, schema, path):
        if "minLength" in schema and len(instance) < schema["minLength"]:
            self.fail(path, "expected at least %d character(s), got %d"
                      % (schema["minLength"], len(instance)))
        if "pattern" in schema and re.search(schema["pattern"], instance) is None:
            self.fail(path, "value %r does not match the pattern %r"
                      % (instance, schema["pattern"]))

    def validate_number(self, instance, schema, path):
        if "minimum" in schema and instance < schema["minimum"]:
            self.fail(path, "expected a value >= %r, got %r" % (schema["minimum"], instance))


def load_json(path, label):
    try:
        with open(path, "r", encoding="utf-8") as handle:
            return json.load(handle)
    except OSError as exc:
        sys.stderr.write("cannot read %s (%s): %s\n" % (label, path, exc))
        sys.exit(2)
    except ValueError as exc:
        sys.stderr.write("%s is not valid JSON (%s): %s\n" % (label, path, exc))
        sys.exit(2)


def main(argv):
    schema_path = argv[1] if len(argv) > 1 else DEFAULT_SCHEMA
    document_path = argv[2] if len(argv) > 2 else DEFAULT_DOCUMENT

    schema = load_json(schema_path, "the schema")
    document = load_json(document_path, "the document")

    unknown = unsupported(schema)
    if unknown:
        sys.stderr.write(
            "the schema uses keyword(s) this validator does not implement: %s\n"
            % ", ".join(unknown))
        sys.stderr.write("refusing to report a pass on a check that was never run.\n")
        return 2

    validator = Validator(schema)
    validator.validate(document, schema, "document")

    if validator.errors:
        print("EFFECT SCHEMA VALIDATION: FAIL (%d violation(s))" % len(validator.errors))
        print("  schema:   %s" % schema_path)
        print("  document: %s" % document_path)
        for error in validator.errors:
            print("  - %s" % error)
        return 1

    entries = document.get("entries", []) if isinstance(document, dict) else []
    print("EFFECT SCHEMA VALIDATION: PASS")
    print("  schema:   %s" % schema_path)
    print("  document: %s" % document_path)
    print("  entries validated: %d" % len(entries))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
