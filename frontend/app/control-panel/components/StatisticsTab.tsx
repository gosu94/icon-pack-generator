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

const rangeOptions: { value: StatsRange; label: string; description: string }[] = [
  { value: "week", label: "Last 7 days", description: "Rolling weekly view" },
  { value: "month", label: "Last 30 days", description: "Rolling monthly view" },
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

        <div className="mt-4 flex flex-wrap gap-6 text-sm text-slate-500">
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-purple-500" />
            New accounts
          </div>
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-emerald-500" />
            Icons generated
          </div>
        </div>

        <div className="mt-6">
          <StatisticsChart
            data={combinedData}
            formatLabel={(value) => dateFormatter.format(new Date(value))}
            formatTooltipLabel={(value) => longDateFormatter.format(new Date(value))}
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
}: {
  data: CombinedDataPoint[];
  formatLabel: (value: string) => string;
  formatTooltipLabel: (value: string) => string;
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

  const maxValue =
    data.reduce(
      (max, item) => Math.max(max, item.registrations, item.icons),
      0
    ) || 1;
  const width = Math.max((data.length - 1) * 80 + 80, 320);
  const height = 240;
  const padding = 24;
  const chartHeight = height - padding * 2;
  const innerWidth = width - padding * 2;
  const xStep = data.length > 1 ? innerWidth / (data.length - 1) : 0;
  const yTicks = 4;

  const buildPoints = (key: "registrations" | "icons") =>
    data
      .map((point, index) => {
        const ratio = (point[key] || 0) / maxValue;
        const x = padding + xStep * index;
        const y = padding + (1 - ratio) * chartHeight;
        return `${x},${y}`;
      })
      .join(" ");

  const registrationPoints = buildPoints("registrations");
  const iconPoints = buildPoints("icons");
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
      <div className="relative">
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
            <div className="mt-1 flex justify-between gap-2">
              <span className="flex items-center gap-1 text-purple-600">
                <span className="h-2 w-2 rounded-full bg-purple-500" />
                {hoveredPoint.registrations.toLocaleString()}
              </span>
              <span className="flex items-center gap-1 text-emerald-600">
                <span className="h-2 w-2 rounded-full bg-emerald-500" />
                {hoveredPoint.icons.toLocaleString()}
              </span>
            </div>
          </div>
        )}
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="h-60 w-full"
          preserveAspectRatio="none"
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
          <polyline
            points={registrationPoints}
            fill="none"
            stroke="#7c3aed"
            strokeWidth="3"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <polyline
            points={iconPoints}
            fill="none"
            stroke="#10b981"
            strokeWidth="3"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          {data.map((point, index) => {
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
          {data.map((point, index) => {
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
