"use client";

import { useEffect, useState } from "react";
import {MessageCircle, MessageSquare, Send, X} from "lucide-react";

const FEEDBACK_STORAGE_KEY = "feedback:lastSubmittedAt";

export default function FeedbackWidget() {
  const [isOpen, setIsOpen] = useState(false);
  const [feedback, setFeedback] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submissionStatus, setSubmissionStatus] = useState<
    "idle" | "success" | "error"
  >("idle");
  const [errorMessage, setErrorMessage] = useState("");
  const [hasSubmittedToday, setHasSubmittedToday] = useState(false);
  const [showThankYouBadge, setShowThankYouBadge] = useState(false);

  const evaluateSubmissionLimit = () => {
    if (typeof window === "undefined") return;
    const stored = localStorage.getItem(FEEDBACK_STORAGE_KEY);
    if (!stored) {
      setHasSubmittedToday(false);
      return;
    }
    const storedDate = new Date(stored);
    if (Number.isNaN(storedDate.getTime())) {
      localStorage.removeItem(FEEDBACK_STORAGE_KEY);
      setHasSubmittedToday(false);
      return;
    }
    if (storedDate.toDateString() === new Date().toDateString()) {
      setHasSubmittedToday(true);
    } else {
      localStorage.removeItem(FEEDBACK_STORAGE_KEY);
      setHasSubmittedToday(false);
    }
  };

  useEffect(() => {
    evaluateSubmissionLimit();
  }, []);

  useEffect(() => {
    if (!showThankYouBadge) return;
    const timer = setTimeout(() => setShowThankYouBadge(false), 4000);
    return () => clearTimeout(timer);
  }, [showThankYouBadge]);

  const markSubmittedToday = () => {
    if (typeof window !== "undefined") {
      localStorage.setItem(FEEDBACK_STORAGE_KEY, new Date().toISOString());
    }
    setHasSubmittedToday(true);
  };

  const toggleWidget = () => {
    if (!isOpen) {
      evaluateSubmissionLimit();
    }
    setIsOpen((prev) => !prev);
    if (isOpen) {
      setSubmissionStatus("idle");
      setErrorMessage("");
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (hasSubmittedToday) {
      setErrorMessage("You have already submitted feedback today. Please try again tomorrow.");
      setSubmissionStatus("error");
      return;
    }

    if (!feedback.trim()) {
      setErrorMessage("Please enter your feedback.");
      setSubmissionStatus("error");
      return;
    }

    setIsSubmitting(true);
    setSubmissionStatus("idle");
    setErrorMessage("");

    try {
      const response = await fetch("/api/feedback", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({ feedback }),
      });

      if (response.ok) {
        setSubmissionStatus("success");
        setFeedback("");
        markSubmittedToday();
        setIsOpen(false);
        setShowThankYouBadge(true);
      } else {
        const errorData = await response.json();
        setErrorMessage(
          errorData.message ||
            "An error occurred while submitting your feedback."
        );
        setSubmissionStatus("error");
        if (response.status === 429) {
          markSubmittedToday();
        }
      }
    } catch (err) {
      setErrorMessage("An unexpected error occurred. Please try again later.");
      setSubmissionStatus("error");
    } finally {
      setIsSubmitting(false);
    }
  };

  const isSubmitDisabled = isSubmitting || hasSubmittedToday;

  return (
    <div className="fixed bottom-6 right-6 z-50 flex flex-col items-end gap-3 text-slate-900">
      {isOpen && (
        <div className="w-80 sm:w-96 rounded-2xl border border-slate-200 bg-white/95 shadow-2xl backdrop-blur-md">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <div>
              <p className="text-sm font-semibold text-slate-800">
                Share your feedback
              </p>
              <p className="text-xs text-slate-500">
                Let us know how we can improve.
              </p>
            </div>
            <button
              type="button"
              onClick={toggleWidget}
              className="rounded-full p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
              aria-label="Close feedback widget"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <div className="px-4 py-3">
            {submissionStatus === "success" && (
              <div className="mb-3 rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700">
                Thanks for the feedback!
              </div>
            )}

            {submissionStatus === "error" && (
              <div className="mb-3 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
                {errorMessage}
              </div>
            )}

            {hasSubmittedToday && (
              <div className="mb-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
                You have already submitted feedback today. Please come back tomorrow.
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-3">
              <div>
                <label
                  htmlFor="feedback-widget-input"
                  className="mb-1 block text-xs font-medium text-slate-600"
                >
                  Your Feedback
                </label>
                <textarea
                  id="feedback-widget-input"
                  rows={4}
                  className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 shadow-inner focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-200"
                  placeholder="Tell us what's on your mind..."
                  value={feedback}
                  onChange={(e) => setFeedback(e.target.value)}
                  disabled={isSubmitDisabled}
                />
              </div>
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={toggleWidget}
                  className="rounded-full border border-slate-200 px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50"
                  disabled={isSubmitting}
                >
                  Close
                </button>
                <button
                  type="submit"
                  className="inline-flex items-center gap-2 rounded-full bg-gradient-to-r from-blue-600 to-purple-600 px-4 py-2 text-sm font-semibold text-white shadow-lg transition hover:from-blue-700 hover:to-purple-700 disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={isSubmitDisabled}
                >
                  {hasSubmittedToday ? "Come back tomorrow" : isSubmitting ? "Sending..." : "Send"}
                  {!isSubmitDisabled && <Send className="h-4 w-4" />}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <div className="flex flex-col items-end gap-2">
        {showThankYouBadge && (
          <div className="rounded-full bg-emerald-500 px-4 py-1 text-xs font-semibold text-white shadow-lg">
            Thanks for your feedback!
          </div>
        )}

        <button
          type="button"
          onClick={toggleWidget}
          aria-label="Open feedback widget"
          className="group relative right-[-24px] flex h-16 w-10 items-center justify-center rounded-l-full border border-white/30 bg-gradient-to-b from-blue-600 to-purple-600 text-white shadow-xl transition hover:from-blue-700 hover:to-purple-700"
        >
          <MessageSquare className="h-5 w-5 transition group-hover:scale-110" />
        </button>
      </div>
    </div>
  );
}
