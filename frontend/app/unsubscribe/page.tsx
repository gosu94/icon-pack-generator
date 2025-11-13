'use client';

import React, { useState, useEffect, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { CheckCircle, AlertCircle, Loader2 } from 'lucide-react';

function UnsubscribeContent() {
  const searchParams = useSearchParams();
  const token = searchParams?.get('token');
  
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!token) {
      setStatus('error');
      setMessage('Invalid unsubscribe link. No token provided.');
      return;
    }

    const unsubscribe = async () => {
      try {
        const response = await fetch(`/api/user/unsubscribe?token=${encodeURIComponent(token)}`, {
          method: 'GET',
          credentials: 'include',
        });

        const data = await response.json();

        if (response.ok && data.success) {
          setStatus('success');
          setMessage(data.message || 'Successfully unsubscribed from notifications');
        } else {
          setStatus('error');
          setMessage(data.message || 'Failed to unsubscribe. The link may be invalid or expired.');
        }
      } catch (error) {
        console.error('Error unsubscribing:', error);
        setStatus('error');
        setMessage('An error occurred while processing your unsubscribe request. Please try again later.');
      }
    };

    unsubscribe();
  }, [token]);

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50/30 flex items-center justify-center p-4">
      <div className="max-w-md w-full bg-white rounded-2xl shadow-xl p-8 text-center">
        {status === 'loading' && (
          <>
            <Loader2 className="w-16 h-16 mx-auto mb-4 text-purple-600 animate-spin" />
            <h1 className="text-2xl font-bold text-slate-800 mb-2">Processing...</h1>
            <p className="text-slate-600">Please wait while we process your unsubscribe request.</p>
          </>
        )}

        {status === 'success' && (
          <>
            <CheckCircle className="w-16 h-16 mx-auto mb-4 text-green-500" />
            <h1 className="text-2xl font-bold text-slate-800 mb-2">Unsubscribed Successfully</h1>
            <p className="text-slate-600 mb-6">{message}</p>
            <p className="text-sm text-slate-500 mb-6">
              You will no longer receive marketing and feedback emails from IconPackGen AI. 
              You may still receive essential account-related communications.
            </p>
            <Link
              href="/"
              className="inline-block px-6 py-3 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors font-medium"
            >
              Return to Home
            </Link>
          </>
        )}

        {status === 'error' && (
          <>
            <AlertCircle className="w-16 h-16 mx-auto mb-4 text-red-500" />
            <h1 className="text-2xl font-bold text-slate-800 mb-2">Unsubscribe Failed</h1>
            <p className="text-slate-600 mb-6">{message}</p>
            <div className="space-y-3">
              <Link
                href="/"
                className="inline-block px-6 py-3 bg-slate-200 text-slate-700 rounded-lg hover:bg-slate-300 transition-colors font-medium mr-2"
              >
                Return to Home
              </Link>
              <Link
                href="/settings"
                className="inline-block px-6 py-3 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors font-medium"
              >
                Go to Settings
              </Link>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export default function UnsubscribePage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50/30 flex items-center justify-center">
        <Loader2 className="w-8 h-8 text-purple-600 animate-spin" />
      </div>
    }>
      <UnsubscribeContent />
    </Suspense>
  );
}

