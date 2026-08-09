.PHONY: compile examples test

compile:
	sbt compile

examples:
	sbt "runMain doppio.examples.RunExamples"
	sbt "runMain doppio.examples.TestAccumulator"

test: compile examples
	sbt test
