"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { XCircle, Home, ArrowRight, CreditCard, RefreshCw } from "lucide-react";
import Navigation from "../../../components/Navigation";

export default function PaymentFailurePage() {
  const router = useRouter();
  const [countdown, setCountdown] = useState(7);

  useEffect(() => {
    const timer = setInterval(() => {
      setCountdown((prev) => prev - 1);
    }, 1000);

    const redirectTimer = setTimeout(() => {
      router.push("/store");
    }, 7000);

    return () => {
      clearInterval(timer);
      clearTimeout(redirectTimer);
    };
  }, [router]);

  const handleGoToStore = () => {
    router.push("/store");
  };

  const handleGoHome = () => {
    router.push("/dashboard");
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50/30">
      <Navigation />
      
      <div className="flex items-center justify-center min-h-[calc(100vh-80px)] px-6 py-12">
        <div className="max-w-lg mx-auto">
          <div className="bg-white/80 backdrop-blur-md rounded-3xl shadow-2xl border border-red-200/50 overflow-hidden">
            {/* Header with failure indication */}
            <div className="bg-gradient-to-br from-red-500 to-rose-600 p-8 text-center text-white relative">
              {/* Decorative elements */}
              <div className="absolute top-4 left-4 w-12 h-12 bg-white/10 rounded-full blur-xl"></div>
              <div className="absolute bottom-4 right-4 w-16 h-16 bg-red-300/20 rounded-full blur-2xl"></div>
              
              <div className="relative z-10">
                <div className="inline-flex items-center justify-center w-20 h-20 bg-white/20 rounded-full mb-6 animate-pulse">
                  <XCircle className="w-10 h-10 text-white" />
                </div>
                <h1 className="text-3xl font-bold mb-2">Payment Cancelled</h1>
                <p className="text-red-100 text-lg">
                  Your payment was not completed
                </p>
              </div>
            </div>

            {/* Content */}
            <div className="p-8 text-center">
              <div className="space-y-6">
                <div className="bg-gradient-to-br from-red-50 to-rose-50 rounded-2xl p-6 border border-red-200/30">
                  <h2 className="text-xl font-semibold text-slate-900 mb-3">
                    😔 Payment was not completed
                  </h2>
                  <p className="text-slate-600 leading-relaxed mb-4">
                    Your payment was cancelled or could not be processed. Don't worry - no charges were made to your account.
                  </p>
                  
                  <div className="text-sm text-slate-500 space-y-2">
                    <p className="flex items-center justify-center space-x-2">
                      <span>Common reasons:</span>
                    </p>
                    <div className="text-xs space-y-1">
                      <p>• Payment was cancelled by user</p>
                      <p>• Card was declined by bank</p>
                      <p>• Network connection issues</p>
                    </div>
                  </div>
                </div>

                {/* Countdown and redirect info */}
                <div className="bg-gradient-to-br from-blue-50 to-purple-50 rounded-2xl p-4 border border-blue-200/30">
                  <p className="text-slate-600 mb-2">
                    Redirecting to store in{" "}
                    <span className="font-bold text-blue-600 text-lg">{countdown}</span>{" "}
                    seconds
                  </p>
                  <div className="w-full bg-gray-200 rounded-full h-2 mb-3">
                    <div 
                      className="bg-gradient-to-r from-blue-500 to-purple-500 h-2 rounded-full transition-all duration-1000 ease-linear"
                      style={{ width: `${((7 - countdown) / 7) * 100}%` }}
                    ></div>
                  </div>
                </div>

                {/* Action buttons */}
                <div className="space-y-3">
                  <button
                    onClick={handleGoToStore}
                    className="w-full bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 text-white font-semibold py-4 px-6 rounded-xl shadow-lg hover:shadow-xl transform hover:scale-[1.02] transition-all duration-200 flex items-center justify-center space-x-2"
                  >
                    <RefreshCw className="w-5 h-5" />
                    <span>Try Again</span>
                    <ArrowRight className="w-5 h-5" />
                  </button>

                  <button
                    onClick={handleGoHome}
                    className="w-full bg-white text-slate-600 hover:text-slate-800 hover:bg-slate-50 border border-slate-200 hover:border-slate-300 font-semibold py-3 px-6 rounded-xl shadow-md hover:shadow-lg transition-all duration-200 flex items-center justify-center space-x-2"
                  >
                    <Home className="w-5 h-5" />
                    <span>Go to Dashboard</span>
                  </button>
                </div>

                <div className="bg-gradient-to-br from-amber-50 to-yellow-50 rounded-xl p-4 border border-amber-200/30">
                  <div className="flex items-center justify-center space-x-2 text-amber-700 mb-2">
                    <CreditCard className="w-4 h-4" />
                    <span className="text-sm font-semibold">Need Help?</span>
                  </div>
                  <p className="text-xs text-amber-600">
                    If you're having payment issues, try using a different card or contact our support team.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
