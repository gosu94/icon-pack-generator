"use client";

import { useState, useEffect } from "react";
import Navigation from "../../components/Navigation";
import Image from "next/image";

export default function StorePage() {
  const plans = [
    { name: "Starter Pack", price: 5, coins: 8, icons: 72, popular: false },
    { name: "Creator Pack", price: 10, coins: 18, icons: 162, popular: true },
    { name: "Pro Pack", price: 20, coins: 40, icons: 360, popular: false },
  ];

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50/30">
      <Navigation />
      <main className="container mx-auto px-4 py-12">
        <div className="text-center mb-12">
          <h1 className="text-4xl font-bold tracking-tight text-gray-900 sm:text-6xl">
            Coin Store
          </h1>
          <p className="mt-6 text-lg leading-8 text-gray-600">
            Purchase generation coins to create more icons.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 max-w-4xl mx-auto">
          {plans.map((plan) => (
            <div
              key={plan.name}
              className={`relative flex flex-col rounded-2xl border ${
                plan.popular
                  ? "border-indigo-600 shadow-2xl"
                  : "border-gray-200"
              } bg-white p-8 text-center`}
            >
              {plan.popular && (
                <div className="absolute top-0 -translate-y-1/2 transform self-center rounded-full bg-indigo-600 px-4 py-1 text-sm font-semibold text-white">
                  Most Popular
                </div>
              )}
              <h3 className="text-lg font-semibold leading-8 text-gray-900">
                {plan.name}
              </h3>
              <p className="mt-4 flex items-baseline justify-center gap-x-2">
                <span className="text-5xl font-bold tracking-tight text-gray-900">
                  ${plan.price}
                </span>
                <span className="text-sm font-semibold leading-6 tracking-wide text-gray-600">
                  USD
                </span>
              </p>
              <div className="mt-6 flex items-center justify-center gap-x-2">
                <Image src="/images/coin.webp" alt="Coins" width={24} height={24} />
                <span className="text-2xl font-bold text-gray-900">
                  {plan.coins} Coins
                </span>
              </div>
              <p className="mt-2 text-sm text-gray-500">
                Generates up to {plan.icons} icons
              </p>
              <button
                className={`mt-8 block w-full rounded-md py-3 text-sm font-semibold ${
                  plan.popular
                    ? "bg-indigo-600 text-white hover:bg-indigo-500"
                    : "bg-white text-indigo-600 ring-1 ring-inset ring-indigo-200 hover:ring-indigo-300"
                }`}
              >
                Buy now
              </button>
            </div>
          ))}
        </div>
      </main>
    </div>
  );
}
