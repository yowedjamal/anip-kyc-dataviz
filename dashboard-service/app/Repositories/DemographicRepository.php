<?php

namespace App\Repositories;

interface DemographicRepository
{
    public function ageDistribution(string $from, string $to): array;
    public function documentTypeDistribution(string $from, string $to): array;
    public function successRatesByCategory(string $from, string $to): array;
}
