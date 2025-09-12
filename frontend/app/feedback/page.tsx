'use client';

import { useState } from 'react';
import Navigation from '../../components/Navigation';

export default function FeedbackPage() {
  const [feedback, setFeedback] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submissionStatus, setSubmissionStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [errorMessage, setErrorMessage] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!feedback.trim()) {
      setErrorMessage('Please enter your feedback.');
      setSubmissionStatus('error');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage('');
    setSubmissionStatus('idle');

    try {
      const response = await fetch('/api/feedback', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify({ feedback }),
      });

      if (response.ok) {
        setSubmissionStatus('success');
        setFeedback('');
      } else {
        const errorData = await response.json();
        setErrorMessage(errorData.message || 'An error occurred while submitting your feedback.');
        setSubmissionStatus('error');
      }
    } catch (error) {
      setErrorMessage('An unexpected error occurred. Please try again later.');
      setSubmissionStatus('error');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-50 via-white to-blue-50/30">
      <Navigation />
      <main className="container mx-auto px-4 py-8">
        <div className="max-w-2xl mx-auto bg-white/80 backdrop-blur-md rounded-3xl p-8 shadow-2xl border-2 border-purple-200/50">
          <div className="absolute inset-0 rounded-3xl bg-gradient-to-br from-white/30 to-transparent pointer-events-none"></div>
          <div className="relative z-10">
            <h1 className="text-3xl font-bold text-slate-900 mb-6">Leave Feedback</h1>
            <p className="text-slate-600 mb-6">We value your feedback! Please let us know how we can improve.</p>

            {submissionStatus === 'success' && (
              <div className="bg-green-100 border-l-4 border-green-500 text-green-700 p-4 mb-6 rounded-md">
                <p className="font-bold">Thank you!</p>
                <p>Your feedback has been submitted successfully.</p>
              </div>
            )}

            {submissionStatus === 'error' && (
              <div className="bg-red-100 border-l-4 border-red-500 text-red-700 p-4 mb-6 rounded-md">
                <p className="font-bold">Error</p>
                <p>{errorMessage}</p>
              </div>
            )}

            <form onSubmit={handleSubmit}>
              <div className="mb-6">
                <label htmlFor="feedback" className="block text-slate-700 font-medium mb-2">Your Feedback</label>
                <textarea
                  id="feedback"
                  rows={6}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-purple-500 focus:border-purple-500 transition duration-150 ease-in-out bg-white/50"
                  placeholder="Enter your feedback here..."
                  value={feedback}
                  onChange={(e) => setFeedback(e.target.value)}
                  disabled={isSubmitting}
                ></textarea>
              </div>

              <div className="text-right">
                <button
                  type="submit"
                  className="px-6 py-3 text-white font-semibold rounded-lg bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 transform hover:scale-[1.02] shadow-lg hover:shadow-xl transition-all duration-200 disabled:bg-gray-400 disabled:cursor-not-allowed"
                  disabled={isSubmitting}
                >
                  {isSubmitting ? 'Submitting...' : 'Submit Feedback'}
                </button>
              </div>
            </form>
          </div>
        </div>
      </main>
    </div>
  );
}
