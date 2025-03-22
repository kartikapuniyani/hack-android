package com.example.sensorsride

data class AccelerometerAnalysis(
  val  avg: Float,
  val  min: Float,
  val  max: Float,
  val  range: Float,
  val  verticalDelta: Float,
  val  potentialPothole: Boolean,
  val  potentialBump: Boolean
)
