.PHONY: compile examples test

compile:
	sbt compile

examples:
	sbt "runMain doppio.examples.RunExamples"
	sbt "runMain doppio.examples.TestAccumulator"
	sbt "runMain doppio.examples.TestAccumulatorWithInterface"
	sbt "runMain doppio.examples.TestTinyAlu"
	sbt "runMain doppio.examples.TestConcurrentFifo"

test: compile examples
	sbt test

tinyalu:
	sbt "runMain doppio.examples.TestTinyAlu"

fifo:
	sbt "runMain doppio.examples.TestConcurrentFifo"

accumulator-interface:
	sbt "runMain doppio.examples.TestAccumulatorWithInterface"
