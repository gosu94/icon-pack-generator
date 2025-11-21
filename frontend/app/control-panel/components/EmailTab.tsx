import { ChangeEvent } from "react";

interface EmailTabProps {
  emailSubject: string;
  emailBody: string;
  emailRecipientScope: "ME" | "EVERYBODY" | "SPECIFIC";
  manualEmail: string;
  requiresManualEmail: boolean;
  isManualEmailValid: boolean;
  emailStatus: string | null;
  emailError: string | null;
  isEmailFormValid: boolean;
  onSubjectChange: (value: string) => void;
  onBodyChange: (value: string) => void;
  onRecipientChange: (value: "ME" | "EVERYBODY" | "SPECIFIC") => void;
  onManualEmailChange: (value: string) => void;
  onRequestSend: () => void;
  onReset: () => void;
}

export default function EmailTab({
  emailSubject,
  emailBody,
  emailRecipientScope,
  manualEmail,
  requiresManualEmail,
  isManualEmailValid,
  emailStatus,
  emailError,
  isEmailFormValid,
  onSubjectChange,
  onBodyChange,
  onRecipientChange,
  onManualEmailChange,
  onRequestSend,
  onReset,
}: EmailTabProps) {
  const handleSubjectChange = (event: ChangeEvent<HTMLInputElement>) => {
    onSubjectChange(event.target.value);
  };

  const handleBodyChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    onBodyChange(event.target.value);
  };

  const handleRecipientChange = (event: ChangeEvent<HTMLSelectElement>) => {
    onRecipientChange(event.target.value as "ME" | "EVERYBODY" | "SPECIFIC");
  };

  const handleManualEmailChange = (event: ChangeEvent<HTMLInputElement>) => {
    onManualEmailChange(event.target.value);
  };

  return (
    <div className="space-y-6 p-6">
      {emailStatus && (
        <div className="rounded-md border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">
          {emailStatus}
        </div>
      )}
      {emailError && (
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {emailError}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="space-y-4">
          <div>
            <label
              htmlFor="emailSubject"
              className="block text-sm font-medium text-slate-700"
            >
              Subject
            </label>
            <input
              id="emailSubject"
              type="text"
              value={emailSubject}
              onChange={handleSubjectChange}
              className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              placeholder="What's your email about?"
            />
          </div>
          <div>
            <label
              htmlFor="emailRecipientScope"
              className="block text-sm font-medium text-slate-700"
            >
              Send To
            </label>
            <select
              id="emailRecipientScope"
              value={emailRecipientScope}
              onChange={handleRecipientChange}
              className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="ME">Me</option>
              <option value="EVERYBODY">Everybody</option>
              <option value="SPECIFIC">Specific email address</option>
            </select>
          </div>
          {requiresManualEmail && (
            <div>
              <label
                htmlFor="manualEmail"
                className="block text-sm font-medium text-slate-700"
              >
                Recipient Email
              </label>
              <input
                id="manualEmail"
                type="email"
                autoComplete="email"
                value={manualEmail}
                onChange={handleManualEmailChange}
                className={`mt-1 block w-full rounded-md border ${
                  manualEmail.trim().length > 0 && !isManualEmailValid
                    ? "border-red-400 focus:border-red-500 focus:ring-red-200"
                    : "border-slate-300 focus:border-indigo-500 focus:ring-indigo-500"
                } bg-white px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2`}
                placeholder="user@example.com"
              />
              <p className="mt-1 text-xs text-slate-500">
                Enter a single email address to send this message to a specific recipient.
              </p>
              {manualEmail.trim().length > 0 && !isManualEmailValid && (
                <p className="mt-1 text-xs text-red-600">
                  Please enter a valid email address.
                </p>
              )}
            </div>
          )}
          <div>
            <label
              htmlFor="emailBody"
              className="block text-sm font-medium text-slate-700"
            >
              HTML Body
            </label>
            <textarea
              id="emailBody"
              value={emailBody}
              onChange={handleBodyChange}
              className="mt-1 block h-64 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              placeholder="<h1>Hello!</h1><p>Write your message here...</p>"
            />
          </div>
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={onRequestSend}
              disabled={!isEmailFormValid}
              className="inline-flex items-center justify-center rounded-md bg-purple-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-purple-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              Send Email
            </button>
            <button
              type="button"
              onClick={onReset}
              className="inline-flex items-center justify-center rounded-md border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-100"
            >
              Clear
            </button>
          </div>
          <p className="text-xs text-slate-500">
            Use HTML to format the email. The preview updates in real time on the right.
          </p>
        </div>
        <div className="flex flex-col rounded-lg border border-slate-200 bg-white shadow-inner">
          <div className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-700">
            Email Preview
          </div>
          <div className="flex-1 overflow-auto px-4 py-4">
            {emailBody.trim() ? (
              <div
                className="prose max-w-none text-slate-800"
                dangerouslySetInnerHTML={{ __html: emailBody }}
              />
            ) : (
              <p className="text-sm text-slate-500">
                Start typing your email to see a live preview.
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
