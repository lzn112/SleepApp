package com.sleepagent.prototype.sleep.staging

interface SleepStageInferenceEngine {
    fun predict(input: Array<FloatArray>): SleepStagePrediction
}
