'use client';

import { useState, useEffect, useMemo, useRef } from "react";
import { Calendar, ChevronLeft, ChevronRight, X } from "lucide-react";

type MonthPickerProps = {
  value: string | null;
  onChange: (value: string) => void;
};

const MONTHS = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

const MonthPicker = ({ value, onChange }: MonthPickerProps) => {
  const [open, setOpen] = useState(false);
  const initialYear = useMemo(() => {
    if (value) {
      const [year] = value.split("-");
      const parsed = parseInt(year, 10);
      if (!Number.isNaN(parsed)) {
        return parsed;
      }
    }
    return new Date().getFullYear();
  }, [value]);
  const [panelYear, setPanelYear] = useState(initialYear);
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setPanelYear(initialYear);
  }, [initialYear]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const handleClickOutside = (event: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  const formattedValue = value
    ? new Intl.DateTimeFormat(undefined, {
        month: "long",
        year: "numeric",
      }).format(new Date(`${value}-01`))
    : "Select month";

  const handleSelect = (monthIndex: number) => {
    const monthValue = `${panelYear}-${String(monthIndex + 1).padStart(2, "0")}`;
    onChange(monthValue);
    setOpen(false);
  };

  const handleClear = (event: React.MouseEvent) => {
    event.stopPropagation();
    onChange("");
    setOpen(false);
  };

  return (
    <div className="relative inline-flex flex-col" ref={containerRef}>
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50"
      >
        <Calendar className="h-4 w-4 text-purple-500" />
        <span>{formattedValue}</span>
        <ChevronDownIcon />
      </button>
      {value && (
        <button
          type="button"
          onClick={handleClear}
          className="mt-1 inline-flex items-center gap-1 text-xs font-semibold text-slate-500 hover:text-slate-700"
        >
          <X className="h-3 w-3" />
          Clear
        </button>
      )}
      {open && (
        <div className="absolute z-20 mt-2 w-64 rounded-xl border border-slate-200 bg-white p-4 shadow-xl">
          <div className="mb-3 flex items-center justify-between">
            <button
              type="button"
              onClick={() => setPanelYear((prev) => prev - 1)}
              className="rounded-lg border border-slate-200 p-1 text-slate-600 hover:bg-slate-50"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <span className="text-sm font-semibold text-slate-700">
              {panelYear}
            </span>
            <button
              type="button"
              onClick={() => setPanelYear((prev) => prev + 1)}
              className="rounded-lg border border-slate-200 p-1 text-slate-600 hover:bg-slate-50"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
          <div className="grid grid-cols-3 gap-2">
            {MONTHS.map((month, idx) => {
              const monthValue = `${panelYear}-${String(idx + 1).padStart(
                2,
                "0"
              )}`;
              const isSelected = value === monthValue;
              return (
                <button
                  key={month}
                  type="button"
                  onClick={() => handleSelect(idx)}
                  className={`rounded-lg border px-2 py-2 text-xs font-semibold transition ${
                    isSelected
                      ? "border-purple-500 bg-purple-600 text-white shadow"
                      : "border-slate-200 text-slate-600 hover:bg-purple-50"
                  }`}
                >
                  {month.slice(0, 3)}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};

const ChevronDownIcon = () => (
  <svg
    className="h-3 w-3 text-slate-500"
    viewBox="0 0 12 12"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <path
      d="M3 4.5L6 7.5L9 4.5"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export default MonthPicker;
