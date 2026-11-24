module {
  hw.module @Accumulator(in %clock : !seq.clock,
                         in %reset : i1,
                         in %en : i1,
                         in %in : i8,
                         out out : i8) {
    %c0 = hw.constant 0 : i8
    %c0b = hw.constant 0 : i8
    %accum = seq.firreg %next clock %clock reset sync %reset, %c0 : i8
    %added = comb.add bin %accum, %in : i8
    %next = comb.mux bin %en, %added, %accum : i8
    hw.output %accum : i8
  }
}
