'use client';

import React from 'react';
import Navigation from '../../components/Navigation';
import Footer from '../../components/Footer';
import Link from 'next/link';

export default function TermsAndConditions() {
  return (
    <>
      <Navigation />
      <div className="min-h-screen bg-gradient-to-br from-slate-50 to-blue-50">
        <div className="max-w-4xl mx-auto px-6 py-12">
          <div className="bg-white rounded-2xl shadow-xl p-8 lg:p-12">
            <h1 className="text-4xl font-bold text-gray-900 mb-8">Terms and Conditions</h1>
            
            <div className="prose prose-lg max-w-none text-gray-700">
              <p className="text-sm text-gray-500 mb-8">
                <strong>Effective Date:</strong> {new Date().toLocaleDateString()}
              </p>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">1. Agreement to Terms</h2>
                <p className="mb-4">
                  By accessing and using Icon Pack Generator, you accept and agree to be bound by the terms and provision of this agreement. If you do not agree to abide by the above, please do not use this service.
                </p>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">2. Service Description</h2>
                <p className="mb-4">
                  Icon Pack Generator is an AI-powered service that allows users to generate custom icon packs. The service operates on a coin-based system where users purchase coins to generate icons.
                </p>
                <ul className="list-disc pl-6 mb-4">
                  <li>Users can create accounts via Google Sign-In or email/password authentication</li>
                  <li>Icons are generated using artificial intelligence technology</li>
                  <li>Service availability is subject to system capacity and maintenance</li>
                </ul>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">3. Account Registration</h2>
                <p className="mb-4">
                  To access our services, you must create an account by providing:
                </p>
                <ul className="list-disc pl-6 mb-4">
                  <li>A valid email address</li>
                  <li>A secure password (if not using Google Sign-In)</li>
                </ul>
                <p className="mb-4">
                  You are responsible for maintaining the confidentiality of your account credentials and for all activities that occur under your account.
                </p>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">4. Payment and Coins</h2>
                <h3 className="text-lg font-semibold text-gray-800 mb-3">4.1 Coin System</h3>
                <ul className="list-disc pl-6 mb-4">
                  <li>Our service operates on a prepaid coin system</li>
                  <li>Coins are required to generate icons</li>
                  <li>Coins are non-transferable and non-refundable</li>
                  <li>Coins do not expire but are tied to your account</li>
                </ul>
                
                <h3 className="text-lg font-semibold text-gray-800 mb-3">4.2 Payments</h3>
                <ul className="list-disc pl-6 mb-4">
                  <li>Payments are processed securely through Stripe</li>
                  <li>We accept major credit cards and payment methods supported by Stripe</li>
                  <li>All payments are processed in real-time</li>
                  <li>Payment confirmation will be provided via email</li>
                </ul>

                <h3 className="text-lg font-semibold text-gray-800 mb-3">4.3 Refunds</h3>
                <p className="mb-4">
                  Coins are generally non-refundable. However, we may provide refunds at our sole discretion in cases of technical errors or service failures that prevent you from using purchased coins.
                </p>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">5. Use of Service</h2>
                <h3 className="text-lg font-semibold text-gray-800 mb-3">5.1 Acceptable Use</h3>
                <p className="mb-4">You agree to use our service only for lawful purposes and in accordance with these terms. You agree not to:</p>
                <ul className="list-disc pl-6 mb-4">
                  <li>Generate content that is illegal, harmful, or violates others' rights</li>
                  <li>Create icons containing copyrighted material without permission</li>
                  <li>Use automated systems to abuse or overload our service</li>
                  <li>Attempt to reverse engineer or exploit our AI technology</li>
                  <li>Share account credentials with others</li>
                </ul>

                <h3 className="text-lg font-semibold text-gray-800 mb-3">5.2 Generated Content</h3>
                <ul className="list-disc pl-6 mb-4">
                  <li>You retain ownership of icons you generate using our service</li>
                  <li>You are responsible for ensuring your use of generated icons complies with applicable laws</li>
                  <li>We do not guarantee that generated content is free from third-party claims</li>
                </ul>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">6. Intellectual Property</h2>
                <p className="mb-4">
                  The Icon Pack Generator service, including its AI technology, user interface, and underlying software, is owned by us and protected by intellectual property laws. You may not copy, modify, or redistribute our service without explicit permission.
                </p>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">7. Service Availability</h2>
                <p className="mb-4">
                  We strive to maintain high availability but do not guarantee uninterrupted service. We may:
                </p>
                <ul className="list-disc pl-6 mb-4">
                  <li>Perform maintenance that temporarily affects service availability</li>
                  <li>Modify or discontinue features with reasonable notice</li>
                  <li>Suspend service to prevent abuse or technical issues</li>
                </ul>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">8. Data and Privacy</h2>
                <p className="mb-4">
                  Your privacy is important to us. Our collection and use of personal information is governed by our <Link href="/privacy" className="text-blue-600 hover:underline">Privacy Policy</Link>, which is incorporated into these terms by reference.
                </p>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">9. Communications</h2>
                <p className="mb-4">
                  By creating an account and providing your email address, you agree that we may send you emails regarding:
                </p>
                <ul className="list-disc pl-6 mb-4">
                  <li>New features and updates to our service</li>
                  <li>Requests for feedback about your experience</li>
                  <li>Important service announcements and changes</li>
                  <li>Account-related notifications (as required for service operation)</li>
                </ul>
                <p className="mb-4">
                  You can opt out of marketing and feedback emails at any time by following the unsubscribe instructions included in those emails or by contacting us directly. However, you may continue to receive essential account-related communications necessary for the operation of the service.
                </p>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">10. Limitation of Liability</h2>
                <p className="mb-4">
                  To the fullest extent permitted by law, Icon Pack Generator and its operators shall not be liable for any indirect, incidental, special, or consequential damages arising from your use of the service, including but not limited to:
                </p>
                <ul className="list-disc pl-6 mb-4">
                  <li>Loss of data or generated content</li>
                  <li>Business interruption or lost profits</li>
                  <li>Third-party claims related to generated content</li>
                </ul>
                <p className="mb-4">
                  Our total liability shall not exceed the amount you paid for coins in the 12 months preceding the claim.
                </p>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">11. Account Termination</h2>
                <p className="mb-4">
                  We may suspend or terminate your account at any time for violation of these terms or other reasonable cause. You may terminate your account at any time by contacting us. Upon termination:
                </p>
                <ul className="list-disc pl-6 mb-4">
                  <li>Your access to the service will be revoked</li>
                  <li>Unused coins may be forfeited (except as required by law)</li>
                  <li>You remain responsible for any outstanding obligations</li>
                </ul>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">12. Governing Law</h2>
                <p className="mb-4">
                  These terms are governed by the laws of Poland. Any disputes arising from these terms or your use of the service shall be subject to the jurisdiction of Polish courts.
                </p>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">13. Changes to Terms</h2>
                <p className="mb-4">
                  We reserve the right to modify these terms at any time. Material changes will be communicated via email or prominent notice on our service. Continued use of the service after changes constitutes acceptance of the new terms.
                </p>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">14. Contact Information</h2>
                <div className="bg-gray-50 p-6 rounded-lg">
                  <p className="mb-2"><strong>Service Provider:</strong> Tomasz Pilarczyk</p>
                  <p className="mb-2"><strong>Address:</strong> Żwirki i Wigury 16, 66-620 Gubin, Poland</p>
                  <p className="mb-2"><strong>Tax ID:</strong> PL9261689360</p>
                  <p className="mb-2"><strong>Email:</strong> support@iconpackgen.com</p>
                </div>
                <p className="mt-4">
                  For questions about these terms or our service, please contact us using the information above.
                </p>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">15. Severability</h2>
                <p className="mb-4">
                  If any provision of these terms is found to be unenforceable, the remaining provisions will continue to be valid and enforceable.
                </p>
              </section>
            </div>

            <div className="mt-12 pt-8 border-t border-gray-200 text-center">
              <Link 
                href="/" 
                className="inline-flex items-center px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors duration-200 font-medium"
              >
                Back to Home
              </Link>
            </div>
          </div>
        </div>
      </div>
      <Footer />
    </>
  );
}
