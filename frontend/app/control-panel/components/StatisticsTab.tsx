'use client';

import { useMemo, useRef, useState } from "react";
import { BarChart3, Loader2, RefreshCw } from "lucide-react";
import { ActivityStats, StatsRange } from "../types";
import MonthPicker from "./MonthPicker";

type StatisticsTabProps = {
  data: ActivityStats | null;
  loading: boolean;
  error: string | null;
  range: StatsRange;
  monthFilter: string | null;
  onRangeChange: (range: StatsRange) => void;
  onMonthChange: (value: string) => void;
  onRetry: () => void;
};

type CombinedDataPoint = {
  date: string;
  registrations: number;
  icons: number;
};

type SeriesKey = "registrations" | "icons";

const rangeOptions: { value: StatsRange; label: string; description: string }[] = [
  { value: "week", label: "Last 7 days", description: "Rolling weekly view" },
  { value: "month", label: "Last 30 days", description: "Rolling monthly view" },
];

const seriesOptions: {
  key: SeriesKey;
  label: string;
  dotClass: string;
  accentClass: string;
}[] = [
  {
    key: "registrations",
    label: "New accounts",
    dotClass: "bg-purple-500",
    accentClass: "text-purple-600",
  },
  {
    key: "icons",
    label: "Icons generated",
    dotClass: "bg-emerald-500",
    accentClass: "text-emerald-600",
  },
];

const StatisticsTab = ({
  data,
  loading,
  error,
  range,
  monthFilter,
  onRangeChange,
  onMonthChange,
  onRetry,
}: StatisticsTabProps) => {
  const combinedData = useMemo<CombinedDataPoint[]>(() => {
    if (!data) {
      return [];
    }

    const iconMap = new Map(data.icons.map((item) => [item.date, item.count]));
    return data.registrations.map((item) => ({
      date: item.date,
      registrations: item.count,
      icons: iconMap.get(item.date) ?? 0,
    }));
  }, [data]);

  const dateFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat(undefined, {
        month: "short",
      day: "numeric",
    }),
    []
  );

  const [visibleSeries, setVisibleSeries] = useState<Record<SeriesKey, boolean>>({
    registrations: true,
    icons: true,
  });

  const activeSeries = useMemo<SeriesKey[]>(() => {
    return (Object.entries(visibleSeries) as [SeriesKey, boolean][])
      .filter(([, isVisible]) => isVisible)
      .map(([key]) => key);
  }, [visibleSeries]);

  const toggleSeries = (series: SeriesKey) => {
    setVisibleSeries((prev) => ({
      ...prev,
      [series]: !prev[series],
    }));
  };

  const longDateFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat(undefined, {
        weekday: "short",
        month: "short",
        day: "numeric",
      }),
    []
  );

  const monthLabel = useMemo(() => {
    if (!monthFilter) {
      return null;
    }
    const date = new Date(`${monthFilter}-01`);
    return new Intl.DateTimeFormat(undefined, {
      month: "long",
      year: "numeric",
    }).format(date);
  }, [monthFilter]);

  const rangeDescription = monthLabel
    ? monthLabel
    : range === "week"
    ? "last 7 days"
    : "last 30 days";

  const controls = (
    <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
      <div className="flex flex-wrap gap-2">
        {rangeOptions.map((option) => (
          <button
            key={option.value}
            onClick={() => onRangeChange(option.value)}
            className={`rounded-lg border px-4 py-2 text-sm font-semibold transition ${
              range === option.value
                ? "border-purple-500 bg-purple-600 text-white shadow"
                : "border-slate-200 bg-white text-slate-600 hover:bg-purple-50"
            }`}
          >
            {option.label}
          </button>
        ))}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm font-medium text-slate-600">
          Specific month
        </span>
        <MonthPicker value={monthFilter} onChange={onMonthChange} />
      </div>
      <button
        onClick={onRetry}
        disabled={loading}
        className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:opacity-50"
      >
        <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
        Refresh data
      </button>
    </div>
  );

  if (loading && !data) {
    return (
      <div className="p-6 space-y-6 bg-white/70">
        {controls}
        <div className="flex flex-col items-center justify-center rounded-xl border border-slate-200 bg-white px-6 py-16 text-center">
          <Loader2 className="h-8 w-8 animate-spin text-purple-500" />
          <p className="mt-4 text-base font-medium text-slate-700">
            Loading statistics...
          </p>
        </div>
      </div>
    );
  }

  if (error && !data) {
    return (
      <div className="p-6 space-y-6 bg-white/70">
        {controls}
        <div className="rounded-xl border border-red-200 bg-red-50/70 px-6 py-6">
          <p className="text-base font-semibold text-red-700">
            Unable to load statistics
          </p>
          <p className="mt-1 text-sm text-red-600">
            {error}
          </p>
          <button
            onClick={onRetry}
            className="mt-4 inline-flex items-center gap-2 rounded-lg border border-red-200 bg-white px-4 py-2 text-sm font-semibold text-red-700 shadow-sm transition hover:bg-red-100"
          >
            <RefreshCw className="h-4 w-4" />
            Try again
          </button>
        </div>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="p-6 space-y-6 bg-white/70">
        {controls}
        <div className="rounded-xl border border-slate-200 bg-white px-6 py-10 text-center text-sm text-slate-600">
          Select a range to load aggregated statistics.
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6 bg-white/70">
      {controls}
      {error && (
        <div className="rounded-lg border border-amber-200 bg-amber-50/80 px-4 py-3 text-sm text-amber-800">
          Unable to refresh statistics: {error}
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        <div className="rounded-xl border border-slate-200 bg-white px-5 py-5 shadow-sm">
          <p className="text-sm font-medium text-slate-500">New accounts</p>
          <p className="mt-2 text-3xl font-bold text-slate-900">
            {data.totalRegistrations.toLocaleString()}
          </p>
          <p className="text-xs text-slate-500">Created in the {rangeDescription}</p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white px-5 py-5 shadow-sm">
          <p className="text-sm font-medium text-slate-500">Icons generated</p>
          <p className="mt-2 text-3xl font-bold text-slate-900">
            {data.totalIcons.toLocaleString()}
          </p>
          <p className="text-xs text-slate-500">Generated in the {rangeDescription}</p>
        </div>
      </div>

      <div className="rounded-xl border border-slate-200 bg-white px-5 py-5 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-lg font-semibold text-slate-800">
              Activity overview
            </p>
            <p className="text-sm text-slate-500">
              Daily registrations vs generated icons
            </p>
          </div>
          <BarChart3 className="h-6 w-6 text-purple-500" />
        </div>

        <div className="mt-4 flex flex-wrap gap-3 text-sm text-slate-500">
          {seriesOptions.map((series) => {
            const isActive = visibleSeries[series.key];
            return (
              <button
                key={series.key}
                type="button"
                onClick={() => toggleSeries(series.key)}
                aria-pressed={isActive}
                className={`inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold transition ${
                  isActive
                    ? "border-slate-300 bg-slate-100 text-slate-700"
                    : "border-dashed border-slate-200 bg-white text-slate-400"
                }`}
              >
                <span
                  className={`h-2 w-2 rounded-full ${series.dotClass} ${
                    isActive ? "" : "opacity-30"
                  }`}
                />
                <span
                  className={`${
                    isActive ? series.accentClass : "text-slate-400"
                  }`}
                >
                  {series.label}
                </span>
              </button>
            );
          })}
        </div>

        <div className="mt-6">
          <StatisticsChart
            data={combinedData}
            formatLabel={(value) => dateFormatter.format(new Date(value))}
            formatTooltipLabel={(value) => longDateFormatter.format(new Date(value))}
            activeSeries={activeSeries}
          />
        </div>
      </div>

      <div className="rounded-xl border border-slate-200 bg-white px-5 py-5 shadow-sm">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold text-slate-800">
            Daily breakdown
          </h3>
          <p className="text-xs text-slate-500 uppercase tracking-wide">
            {rangeDescription}
          </p>
        </div>
        {combinedData.length === 0 ? (
          <p className="mt-4 text-sm text-slate-500">
            No activity recorded for the selected range.
          </p>
        ) : (
          <div className="mt-4 divide-y divide-slate-100">
            {combinedData.map((point) => (
              <div
                key={point.date}
                className="grid grid-cols-3 items-center gap-3 py-3 text-sm text-slate-600"
              >
                <span>{longDateFormatter.format(new Date(point.date))}</span>
                <span className="text-right font-semibold text-purple-600">
                  {point.registrations.toLocaleString()}
                </span>
                <span className="text-right font-semibold text-emerald-600">
                  {point.icons.toLocaleString()}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};


export default StatisticsTab;

const StatisticsChart = ({
  data,
  formatLabel,
  formatTooltipLabel,
  activeSeries,
}: {
  data: CombinedDataPoint[];
  formatLabel: (value: string) => string;
  formatTooltipLabel: (value: string) => string;
  activeSeries: SeriesKey[];
}) => {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const svgRef = useRef<SVGSVGElement | null>(null);

  if (data.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50/80 py-10 text-center text-sm text-slate-500">
        No data available for this range.
      </div>
    );
  }

  if (activeSeries.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50/80 py-10 text-center text-sm text-slate-500">
        Enable at least one statistic to display the chart.
      </div>
    );
  }

  const activeSeriesSet = new Set(activeSeries);
  const rawMaxValue = data.reduce((max, item) => {
    let localMax = max;
    if (activeSeriesSet.has("registrations")) {
      localMax = Math.max(localMax, item.registrations);
    }
    if (activeSeriesSet.has("icons")) {
      localMax = Math.max(localMax, item.icons);
    }
    return localMax;
  }, 0);
  const maxValue = rawMaxValue || 1;
  const width = Math.max((data.length - 1) * 80 + 80, 320);
  const height = 240;
  const padding = 24;
  const chartHeight = height - padding * 2;
  const innerWidth = width - padding * 2;
  const xStep = data.length > 1 ? innerWidth / (data.length - 1) : 0;
  const yTicks = 4;
  const yTickValues =
    rawMaxValue === 0
      ? Array(yTicks + 1).fill(0)
      : [...Array(yTicks + 1)].map((_, index) => {
          const ratio = 1 - index / yTicks;
          if (index === yTicks) {
            return 0;
          }
          return Math.round(maxValue * ratio);
        });

  const buildPoints = (key: "registrations" | "icons") =>
    data
      .map((point, index) => {
        const ratio = (point[key] || 0) / maxValue;
        const x = padding + xStep * index;
        const y = padding + (1 - ratio) * chartHeight;
        return `${x},${y}`;
      })
      .join(" ");

  const registrationPoints = activeSeriesSet.has("registrations")
    ? buildPoints("registrations")
    : "";
  const iconPoints = activeSeriesSet.has("icons")
    ? buildPoints("icons")
    : "";
  const hoveredPoint =
    hoveredIndex !== null ? data[hoveredIndex] : null;
  const hoveredX =
    hoveredIndex !== null ? padding + xStep * hoveredIndex : null;

  const handleMouseMove = (
    event: React.MouseEvent<SVGSVGElement, MouseEvent>
  ) => {
    if (!svgRef.current || data.length === 0) {
      return;
    }
    const rect = svgRef.current.getBoundingClientRect();
    const relativeSvgX =
      rect.width > 0
        ? ((event.clientX - rect.left) / rect.width) * width
        : 0;
    const offsetFromPadding = relativeSvgX - padding;
    const clamped =
      innerWidth <= 0
        ? 0
        : Math.max(0, Math.min(offsetFromPadding, innerWidth));
    const ratio = innerWidth <= 0 ? 0 : clamped / innerWidth;
    const index = Math.round(ratio * (data.length - 1));
    setHoveredIndex(index);
  };

  const handleMouseLeave = () => {
    setHoveredIndex(null);
  };

  return (
    <div className="space-y-3">
      <div className="flex gap-4">
        <div
          className="flex w-12 flex-col justify-between text-right text-xs text-slate-400"
          style={{ paddingTop: `${padding}px`, paddingBottom: `${padding}px` }}
        >
          {yTickValues.map((value, index) => (
            <span key={`tick-${index}`} className="tabular-nums">
              {value.toLocaleString()}
            </span>
          ))}
        </div>
        <div className="relative flex-1">
          {hoveredPoint && hoveredX !== null && (
            <div
              className="pointer-events-none absolute -top-2 z-10 w-48 -translate-x-1/2 -translate-y-full rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs text-slate-600 shadow-lg"
              style={{
                left: `${(hoveredX / width) * 100}%`,
              }}
            >
              <p className="text-sm font-semibold text-slate-800">
                {formatTooltipLabel(hoveredPoint.date)}
              </p>
              <div
                className={`mt-1 ${
                  activeSeriesSet.size > 1
                    ? "flex justify-between gap-2"
                    : "flex gap-2"
                }`}
              >
                {activeSeriesSet.has("registrations") && (
                  <span className="flex items-center gap-1 text-purple-600">
                    <span className="h-2 w-2 rounded-full bg-purple-500" />
                    {hoveredPoint.registrations.toLocaleString()}
                  </span>
                )}
                {activeSeriesSet.has("icons") && (
                  <span className="flex items-center gap-1 text-emerald-600">
                    <span className="h-2 w-2 rounded-full bg-emerald-500" />
                    {hoveredPoint.icons.toLocaleString()}
                  </span>
                )}
              </div>
            </div>
          )}
          <svg
            viewBox={`0 0 ${width} ${height}`}
            className="w-full"
            style={{ aspectRatio: `${width} / ${height}` }}
            preserveAspectRatio="xMidYMid meet"
            ref={svgRef}
            onMouseMove={handleMouseMove}
            onMouseLeave={handleMouseLeave}
          >
            {[...Array(yTicks + 1)].map((_, index) => {
              const y = padding + (chartHeight / yTicks) * index;
              return (
                <line
                  key={index}
                  x1={padding}
                  x2={width - padding}
                  y1={y}
                  y2={y}
                  stroke="#e2e8f0"
                  strokeWidth="1"
                />
              );
            })}
            {hoveredPoint && hoveredX !== null && (
              <line
                x1={hoveredX}
                x2={hoveredX}
                y1={padding}
                y2={height - padding}
                stroke="#94a3b8"
                strokeDasharray="4"
              />
            )}
            {activeSeriesSet.has("registrations") && (
              <polyline
                points={registrationPoints}
                fill="none"
                stroke="#7c3aed"
                strokeWidth="3"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            )}
            {activeSeriesSet.has("icons") && (
              <polyline
                points={iconPoints}
                fill="none"
                stroke="#10b981"
                strokeWidth="3"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            )}
            {activeSeriesSet.has("registrations") &&
              data.map((point, index) => {
                const ratio = (point.registrations || 0) / maxValue;
                const x = padding + xStep * index;
                const y = padding + (1 - ratio) * chartHeight;
                const isHovered = hoveredIndex === index;
                return (
                  <circle
                    key={`reg-${point.date}`}
                    cx={x}
                    cy={y}
                    r={isHovered ? 5 : 4}
                    fill="#7c3aed"
                    stroke="#fff"
                    strokeWidth="2"
                  />
                );
              })}
            {activeSeriesSet.has("icons") &&
              data.map((point, index) => {
                const ratio = (point.icons || 0) / maxValue;
                const x = padding + xStep * index;
                const y = padding + (1 - ratio) * chartHeight;
                const isHovered = hoveredIndex === index;
                return (
                  <circle
                    key={`icon-${point.date}`}
                    cx={x}
                    cy={y}
                    r={isHovered ? 5 : 4}
                    fill="#10b981"
                    stroke="#fff"
                    strokeWidth="2"
                  />
                );
              })}
          </svg>
        </div>
      </div>
      <div
        className="grid gap-2 text-center text-xs text-slate-500"
        style={{ gridTemplateColumns: `repeat(${data.length}, minmax(0, 1fr))` }}
      >
        {data.map((point) => (
          <span key={point.date}>{formatLabel(point.date)}</span>
        ))}
      </div>
    </div>
  );
};
