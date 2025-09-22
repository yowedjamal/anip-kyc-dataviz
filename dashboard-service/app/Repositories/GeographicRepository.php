<?php

namespace App\Repositories;

interface GeographicRepository
{
    public function regionalDistribution(string $from, string $to): array;
    public function heatmapData(string $from, string $to): array;
}
