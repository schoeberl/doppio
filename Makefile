.PHONY: compile examples test ports fifo-ports-java accumulator-wrapper-java tinyalu-wrapper-java tinyalu fifo accumulator-interface

compile:
	sbt compile

examples:
	sbt "runMain doppio.examples.RunExamples"
	sbt "runMain doppio.examples.TestAccumulator"
	sbt "runMain doppio.examples.TestAccumulatorWithInterface"
	sbt "runMain doppio.examples.TestAccumulatorScalaBinding"
	sbt "runMain doppio.examples.TestTinyAlu"
	sbt "runMain doppio.examples.TestConcurrentFifo"

test: compile examples
	sbt test

ports:
	scripts/extract_verilog_ports.py src/verilog/accumulator.v
	scripts/extract_verilog_ports.py src/verilog/simple_fifo.v
	scripts/extract_verilog_ports.py src/verilog/tinyalu.sv

fifo-ports-java:
	scripts/extract_verilog_ports.py src/verilog/simple_fifo.v --format java-config --exclude clk

accumulator-wrapper-java:
	scripts/extract_verilog_ports.py src/verilog/accumulator.v --format java-wrapper --exclude clk --class-name AccumulatorInterface

tinyalu-wrapper-java:
	scripts/extract_verilog_ports.py src/verilog/tinyalu.sv --format java-wrapper --exclude clk --class-name TinyAluInterface

tinyalu:
	sbt "runMain doppio.examples.TestTinyAlu"

fifo:
	sbt "runMain doppio.examples.TestConcurrentFifo"

accumulator-interface:
	sbt "runMain doppio.examples.TestAccumulatorWithInterface"

accumulator-scala:
	sbt "runMain doppio.examples.TestAccumulatorScalaBinding"
