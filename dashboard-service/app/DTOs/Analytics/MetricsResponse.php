<?php

namespace App\DTOs\Analytics;

use Carbon\Carbon;

class MetricsResponse
{
    private int $total_sessions = 0;
    private int $completed_sessions = 0;
    private int $failed_sessions = 0;
    private float $completion_rate = 0.0;

    private float $average_processing_time = 0.0;
    private array $processing_time_percentiles = [];
    private array $throughput_per_hour = [];

    private float $document_accuracy = 0.0;
    private float $face_match_confidence = 0.0;
    private float $liveness_success_rate = 0.0;

    private array $anomalies = [];
    private ?Carbon $generatedAt = null;
    private array $dataRange = [];

    public function setTotalSessions(int $v): void { $this->total_sessions = $v; }
    public function setCompletedSessions(int $v): void { $this->completed_sessions = $v; }
    public function setFailedSessions(int $v): void { $this->failed_sessions = $v; }
    public function setCompletionRate(float $v): void { $this->completion_rate = $v; }

    public function setAverageProcessingTime(float $v): void { $this->average_processing_time = $v; }
    public function setProcessingTimePercentiles(array $v): void { $this->processing_time_percentiles = $v; }
    public function setThroughputPerHour(array $v): void { $this->throughput_per_hour = $v; }

    public function setDocumentAccuracy(float $v): void { $this->document_accuracy = $v; }
    public function setFaceMatchConfidence(float $v): void { $this->face_match_confidence = $v; }
    public function setLivenessSuccessRate(float $v): void { $this->liveness_success_rate = $v; }

    public function setAnomalies(array $v): void { $this->anomalies = $v; }

    public function setGeneratedAt(Carbon $dt): void { $this->generatedAt = $dt; }

    public function setDataRange(Carbon $start, Carbon $end): void
    {
        $this->dataRange = [
            'from' => $start->toIso8601String(),
            'to' => $end->toIso8601String(),
        ];
    }

    public function toArray(): array
    {
        return [
            'summary' => [
                'total_sessions' => $this->total_sessions,
                'completed_sessions' => $this->completed_sessions,
                'failed_sessions' => $this->failed_sessions,
                'completion_rate' => $this->completion_rate,
                'average_processing_time' => $this->average_processing_time,
            ],
            'metrics' => [
                'processing_time_percentiles' => $this->processing_time_percentiles,
                'throughput_per_hour' => $this->throughput_per_hour,
            ],
            'quality' => [
                'document_accuracy' => $this->document_accuracy,
                'face_match_confidence' => $this->face_match_confidence,
                'liveness_success_rate' => $this->liveness_success_rate,
            ],
            'anomalies' => $this->anomalies,
            'generated_at' => $this->generatedAt?->toIso8601String(),
            'data_range' => $this->dataRange,
        ];
    }
}
