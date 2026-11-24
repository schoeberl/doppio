module {
  hw.module @Counter(in %clock : !seq.clock, in %reset : i1, in %io_enable : i1, out io_count : i4) {
    %c1_i4 = hw.constant 1 : i4
    %c0_i4 = hw.constant 0 : i4
    %counter = seq.firreg %1 clock %clock reset sync %reset, %c0_i4 {firrtl.random_init_start = 0 : ui64, sv.namehint = "counter"} : i4
    %0 = comb.add bin %counter, %c1_i4 {sv.namehint = "_counter_T"} : i4
    %1 = comb.mux bin %io_enable, %0, %counter : i4
    hw.output %counter : i4
  }
  sv.macro.decl @layers_Counter_Verification["layers_Counter_Verification"]
  emit.file "verification/layers-Counter-Verification.sv" {
    sv.ifdef  @layers_Counter_Verification {
    } else {
      sv.macro.def @layers_Counter_Verification ""
    }
  }
  sv.macro.decl @layers_Counter_Verification_Assert["layers_Counter_Verification_Assert"]
  emit.file "verification/assert/layers-Counter-Verification-Assert.sv" {
    sv.ifdef  @layers_Counter_Verification_Assert {
    } else {
      sv.macro.def @layers_Counter_Verification_Assert ""
      sv.include  local "layers-Counter-Verification.sv"
    }
  }
  sv.macro.decl @layers_Counter_Verification_Assume["layers_Counter_Verification_Assume"]
  emit.file "verification/assume/layers-Counter-Verification-Assume.sv" {
    sv.ifdef  @layers_Counter_Verification_Assume {
    } else {
      sv.macro.def @layers_Counter_Verification_Assume ""
      sv.include  local "layers-Counter-Verification.sv"
    }
  }
  sv.macro.decl @layers_Counter_Verification_Cover["layers_Counter_Verification_Cover"]
  emit.file "verification/cover/layers-Counter-Verification-Cover.sv" {
    sv.ifdef  @layers_Counter_Verification_Cover {
    } else {
      sv.macro.def @layers_Counter_Verification_Cover ""
      sv.include  local "layers-Counter-Verification.sv"
    }
  }
  om.class @Counter_Class(%basepath: !om.basepath) {
    om.class.fields 
  }
}
