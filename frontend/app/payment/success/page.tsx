"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { CheckCircle, Home, ArrowRight } from "lucide-react";
import Navigation from "../../../components/Navigation";

export default function PaymentSuccessPage() {
  const router = useRouter();
  const [countdown, setCountdown] = useState(7);

  useEffect(() => {
    const timer = setInterval(() => {
      setCountdown((prev) => prev - 1);
    }, 1000);

    const redirectTimer = setTimeout(() => {
      router.push("/dashboard");
    }, 7000);

    return () => {
      clearInterval(timer);
      clearTimeout(redirectTimer);
    };
  }, [router]);

  const handleGoHome = () => {
    router.push("/dashboard");
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50/30">
      <Navigation />
      
      <div className="flex items-center justify-center min-h-[calc(100vh-80px)] px-6 py-12">
        <div className="max-w-lg mx-auto">
          <div className="bg-white/80 backdrop-blur-md rounded-3xl shadow-2xl border border-green-200/50 overflow-hidden">
            {/* Header with success animation */}
            <div className="bg-gradient-to-br from-green-500 to-emerald-600 p-8 text-center text-white relative">
              {/* Decorative elements */}
              <div className="absolute top-4 left-4 w-12 h-12 bg-white/10 rounded-full blur-xl"></div>
              <div className="absolute bottom-4 right-4 w-16 h-16 bg-green-300/20 rounded-full blur-2xl"></div>
              
              <div className="relative z-10">
                <div className="inline-flex items-center justify-center w-20 h-20 bg-white/20 rounded-full mb-6 animate-bounce">
                  <CheckCircle className="w-10 h-10 text-white" />
                </div>
                <h1 className="text-3xl font-bold mb-2">Payment Successful!</h1>
                <p className="text-green-100 text-lg">
                  Your coins have been added to your account
                </p>
              </div>
            </div>

            {/* Content */}
            <div className="p-8 text-center">
              <div className="space-y-6">
                <div className="bg-gradient-to-br from-green-50 to-emerald-50 rounded-2xl p-6 border border-green-200/30">
                  <h2 className="text-xl font-semibold text-slate-900 mb-3">
                    🎉 Thank you for your purchase!
                  </h2>
                  <p className="text-slate-600 leading-relaxed mb-4">
                    Your payment has been processed successfully and your coins have been credited to your account. 
                    You can now continue generating amazing icons!
                  </p>
                  
                  <div className="flex items-center justify-center space-x-2 text-sm text-slate-500">
                    <span>✓ Payment confirmed</span>
                    <span>•</span>
                    <span>✓ Coins added</span>
                    <span>•</span>
                    <span>✓ Ready to use</span>
                  </div>
                </div>

                {/* Countdown and redirect info */}
                <div className="bg-gradient-to-br from-blue-50 to-purple-50 rounded-2xl p-4 border border-blue-200/30">
                  <p className="text-slate-600 mb-2">
                    Redirecting to dashboard in{" "}
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

                {/* Action button */}
                <button
                  onClick={handleGoHome}
                  className="w-full bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 text-white font-semibold py-4 px-6 rounded-xl shadow-lg hover:shadow-xl transform hover:scale-[1.02] transition-all duration-200 flex items-center justify-center space-x-2"
                >
                  <Home className="w-5 h-5" />
                  <span>Go to Dashboard Now</span>
                  <ArrowRight className="w-5 h-5" />
                </button>

                <p className="text-sm text-slate-400">
                  Need help? Contact our support team anytime.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
