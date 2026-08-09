.PHONY: compile examples test

compile:
	sbt compile

examples:
	sbt "runMain doppio.examples.RunExamples"
	sbt "runMain doppio.examples.RunVerilogAccumulator"

test: compile examples
	sbt test
