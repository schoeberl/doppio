#!/usr/bin/env python3
"""Extract ports from a simple one-module Verilog/SystemVerilog file.

This is intentionally lightweight. It handles ANSI-style module headers like:

    module example #(parameter WIDTH = 8) (
        input wire clk,
        input wire [WIDTH-1:0] in,
        output logic [WIDTH-1:0] out
    );

It is not a full Verilog parser.
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path


@dataclass(frozen=True)
class Port:
    direction: str
    name: str
    width: str | None


JAVA_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "instanceof", "int", "interface", "long", "native", "new",
    "package", "private", "protected", "public", "return", "short", "static",
    "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "try", "void", "volatile", "while",
}


def strip_comments(source: str) -> str:
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL)
    return re.sub(r"//.*", "", source)


def find_matching(source: str, start: int, open_char: str, close_char: str) -> int:
    depth = 0
    for index in range(start, len(source)):
        char = source[index]
        if char == open_char:
            depth += 1
        elif char == close_char:
            depth -= 1
            if depth == 0:
                return index
    raise ValueError(f"missing matching {close_char}")


def find_module_header(source: str) -> tuple[str, str]:
    match = re.search(r"\bmodule\s+([A-Za-z_][A-Za-z0-9_$]*)\b", source)
    if not match:
        raise ValueError("no module declaration found")

    module_name = match.group(1)
    index = match.end()
    while index < len(source) and source[index].isspace():
        index += 1

    if source.startswith("#", index):
        index += 1
        while index < len(source) and source[index].isspace():
            index += 1
        if index >= len(source) or source[index] != "(":
            raise ValueError("parameter block starts with # but has no opening parenthesis")
        index = find_matching(source, index, "(", ")") + 1

    while index < len(source) and source[index].isspace():
        index += 1
    if index >= len(source) or source[index] != "(":
        raise ValueError("module declaration has no ANSI-style port list")

    end = find_matching(source, index, "(", ")")
    return module_name, source[index + 1:end]


def split_top_level_commas(text: str) -> list[str]:
    parts: list[str] = []
    start = 0
    bracket_depth = 0
    paren_depth = 0
    for index, char in enumerate(text):
        if char == "[":
            bracket_depth += 1
        elif char == "]":
            bracket_depth -= 1
        elif char == "(":
            paren_depth += 1
        elif char == ")":
            paren_depth -= 1
        elif char == "," and bracket_depth == 0 and paren_depth == 0:
            parts.append(text[start:index].strip())
            start = index + 1
    tail = text[start:].strip()
    if tail:
        parts.append(tail)
    return parts


def parse_ports(port_list: str) -> list[Port]:
    ports: list[Port] = []
    current_direction: str | None = None
    current_width: str | None = None

    for declaration in split_top_level_commas(port_list):
        declaration = re.sub(r"\s+", " ", declaration.strip())
        declaration = declaration.rstrip(";")
        if not declaration:
            continue

        direction_match = re.match(r"^(input|output|inout)\b\s*(.*)$", declaration)
        if direction_match:
            current_direction = direction_match.group(1)
            declaration = direction_match.group(2).strip()
            current_width = None

        if current_direction is None:
            raise ValueError(f"port lacks direction: {declaration}")

        declaration = re.sub(r"^(wire|reg|logic|signed)\b\s*", "", declaration)
        declaration = re.sub(r"^(wire|reg|logic|signed)\b\s*", "", declaration)

        width_match = re.match(r"^(\[[^\]]+\])\s*(.*)$", declaration)
        if width_match:
            current_width = width_match.group(1)
            declaration = width_match.group(2).strip()

        name_match = re.match(r"^([A-Za-z_][A-Za-z0-9_$]*)\b", declaration)
        if not name_match:
            raise ValueError(f"cannot parse port name from: {declaration}")

        ports.append(Port(current_direction, name_match.group(1), current_width))

    return ports


def parse_file(path: Path) -> tuple[str, list[Port]]:
    source = strip_comments(path.read_text(encoding="utf-8"))
    module_name, port_list = find_module_header(source)
    return module_name, parse_ports(port_list)


def print_table(module_name: str, ports: list[Port]) -> None:
    print(f"module {module_name}")
    for port in ports:
        width = f"{port.width} " if port.width else ""
        print(f"{port.direction:<6} {width}{port.name}")


def print_java_config(ports: list[Port], exclude: set[str]) -> None:
    selected = [port for port in ports if port.name not in exclude]
    for index, port in enumerate(selected):
        if port.direction == "inout":
            raise ValueError("VerilatorConfig Java output does not support inout ports")
        factory = "input" if port.direction == "input" else "output"
        comma = "," if index < len(selected) - 1 else ""
        print(f'VerilatorConfig.{factory}("{port.name}"){comma}')


def to_camel_case(name: str) -> str:
    parts = re.split(r"[^A-Za-z0-9]+|_", name)
    return "".join(part[:1].upper() + part[1:] for part in parts if part)


def to_java_identifier(name: str) -> str:
    identifier = re.sub(r"[^A-Za-z0-9_]", "_", name)
    if not identifier:
        raise ValueError(f"cannot convert port name to Java identifier: {name}")
    if identifier[0].isdigit():
        identifier = "_" + identifier
    if identifier in JAVA_KEYWORDS:
        identifier += "_"
    return identifier


def print_java_wrapper(
        module_name: str,
        ports: list[Port],
        exclude: set[str],
        class_name: str | None,
        package_name: str | None) -> None:
    selected = [port for port in ports if port.name not in exclude]
    if any(port.direction == "inout" for port in selected):
        raise ValueError("Java wrapper output does not support inout ports")

    wrapper_name = class_name or f"{to_camel_case(module_name)}Interface"

    if package_name:
        print(f"package {package_name};")
        print()

    print("import doppio.InPort;")
    print("import doppio.OutPort;")
    print("import doppio.Sim;")
    print()
    print(f"public final class {wrapper_name} {{")
    print("    private final Sim sim;")
    for port in selected:
        port_type = "InPort" if port.direction == "input" else "OutPort"
        print(f"    public final {port_type} {to_java_identifier(port.name)};")

    print()
    print(f"    public {wrapper_name}(Sim sim) {{")
    print("        this.sim = sim;")
    for port in selected:
        field = to_java_identifier(port.name)
        factory = "inPort" if port.direction == "input" else "outPort"
        print(f'        this.{field} = sim.{factory}("{port.name}");')
    print("    }")
    print()
    print("    public Sim sim() {")
    print("        return sim;")
    print("    }")
    print("}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("verilog_file", type=Path)
    parser.add_argument(
            "--format",
            choices=("table", "json", "java-config", "java-wrapper"),
            default="table",
            help="output format")
    parser.add_argument(
            "--exclude",
            action="append",
            default=[],
            help="port name to omit; may be repeated, e.g. --exclude clk")
    parser.add_argument(
            "--class-name",
            help="class name for --format java-wrapper; defaults to <ModuleName>Interface")
    parser.add_argument(
            "--package",
            help="optional Java package declaration for --format java-wrapper")
    args = parser.parse_args()

    module_name, ports = parse_file(args.verilog_file)
    exclude = set(args.exclude)

    if args.format == "table":
        print_table(module_name, ports)
    elif args.format == "json":
        print(json.dumps({
            "module": module_name,
            "ports": [asdict(port) for port in ports if port.name not in exclude],
        }, indent=2))
    elif args.format == "java-config":
        print_java_config(ports, exclude)
    else:
        print_java_wrapper(module_name, ports, exclude, args.class_name, args.package)


if __name__ == "__main__":
    main()
