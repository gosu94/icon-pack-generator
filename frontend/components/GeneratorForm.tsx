import React from "react";
import Image from "next/image";

interface GeneratorFormProps {
  inputType: string;
  setInputType: (value: string) => void;
  
  generalDescription: string;
  setGeneralDescription: (value: string) => void;
  individualDescriptions: string[];
  setIndividualDescriptions: (value: string[]) => void;
  referenceImage: File | null;
  imagePreview: string;
  isGenerating: boolean;
  generateIcons: () => void;
  handleImageSelect: (event: React.ChangeEvent<HTMLInputElement>) => void;
  removeImage: () => void;
  fileInputRef: React.RefObject<HTMLInputElement>;
  formatFileSize: (bytes: number) => string;
}

const GeneratorForm: React.FC<GeneratorFormProps> = ({
  inputType,
  setInputType,
  
  generalDescription,
  setGeneralDescription,
  individualDescriptions,
  setIndividualDescriptions,
  referenceImage,
  imagePreview,
  isGenerating,
  generateIcons,
  handleImageSelect,
  removeImage,
  fileInputRef,
  formatFileSize,
}) => {
  const renderIconFields = () => {
    const count = 9;
    if (isNaN(count)) return null;

    const fields = [];
    for (let i = 0; i < count; i++) {
      fields.push(
        <input
          key={i}
          type="text"
          placeholder={`Icon ${i + 1} description (optional)`}
          value={individualDescriptions[i] || ""}
          onChange={(e) => {
            const newDescriptions = [...individualDescriptions];
            newDescriptions[i] = e.target.value;
            setIndividualDescriptions(newDescriptions);
          }}
          className="w-full px-3 py-2 border border-gray-200 rounded-md text-sm focus:outline-none focus:ring-1 focus:ring-gray-300"
          data-oid="utuin-s"
        />,
      );
    }
    return (
      <div className="grid grid-cols-3 gap-3" data-oid="hmbprf9">
        {fields}
      </div>
    );
  };

  return (
    <div className="w-1/3 p-8" data-oid=".5jo.8i">
      <div
        className="bg-white/80 backdrop-blur-md rounded-3xl p-8 shadow-2xl border-2 border-purple-200/50 h-full overflow-y-auto relative"
        data-oid="l3nyxqb"
      >
        <div
          className="absolute inset-0 rounded-3xl bg-gradient-to-br from-white/30 to-transparent pointer-events-none"
          data-oid="56u82tz"
        ></div>
        <div className="relative z-10" data-oid="7_67d:p">
          <h2
            className="text-2xl font-bold text-slate-900 mb-8"
            data-oid="chy.mp_"
          >
            Icon Pack Generator
          </h2>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              generateIcons();
            }}
            className="space-y-8"
            data-oid="q9a5ht9"
          >
            <div data-oid="1mlykbw">
              <label
                className="block text-lg font-semibold text-slate-900 mb-6"
                data-oid="iazz1yl"
              >
                Choose input type
              </label>
              <div
                className="bg-slate-100 p-1.5 rounded-2xl flex"
                data-oid="s9825hx"
              >
                <button
                  type="button"
                  onClick={() => setInputType("text")}
                  className={`flex-1 px-5 py-4 rounded-xl text-sm font-semibold transition-all duration-300 flex items-center justify-center space-x-2 ${inputType === "text" ? "bg-white text-slate-900 shadow-lg shadow-slate-200/50" : "text-slate-600 hover:text-slate-900 hover:bg-white/50"}`}
                  data-oid="1tmjnjh"
                >
                  <svg
                    className="w-4 h-4"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                    data-oid="r81_4xh"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                      data-oid="oyl9h-v"
                    />
                  </svg>
                  <span data-oid="q36hak1">Text Description</span>
                </button>
                <button
                  type="button"
                  onClick={() => setInputType("image")}
                  className={`flex-1 px-5 py-4 rounded-xl text-sm font-semibold transition-all duration-300 flex items-center justify-center space-x-2 ${inputType === "image" ? "bg-white text-slate-900 shadow-lg shadow-slate-200/50" : "text-slate-600 hover:text-slate-900 hover:bg-white/50"}`}
                  data-oid="hdn2ne9"
                >
                  <svg
                    className="w-4 h-4"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                    data-oid="d.l2zs."
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                      data-oid="0i-4v7u"
                    />
                  </svg>
                  <span data-oid="g5gpafy">Reference Image</span>
                </button>
              </div>
            </div>

            <div data-oid="_.5jto0">
              {inputType === "text" ? (
                <div data-oid="zwfv0z:">
                  <label
                    className="block text-lg font-semibold text-slate-900 mb-4"
                    data-oid="5s95r1i"
                  >
                    General Theme Description
                  </label>
                  <textarea
                    rows={5}
                    value={generalDescription}
                    onChange={(e) => setGeneralDescription(e.target.value)}
                    required={inputType === "text"}
                    className="w-full px-5 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-base placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent focus:bg-white transition-all duration-200 resize-none"
                    placeholder="Describe the general theme for your icon pack... (e.g., minimalist business icons, colorful social media icons, etc.)"
                    data-oid="ejikj:q"
                  />
                </div>
              ) : (
                <div data-oid="s7wsnqu">
                  <label
                    className="block text-sm font-medium text-gray-900 mb-2"
                    data-oid=":ho_1in"
                  >
                    Reference Image
                  </label>
                  <div className="space-y-3" data-oid="dd0fi:d">
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept="image/*"
                      required={inputType === "image"}
                      onChange={handleImageSelect}
                      className="w-full px-3 py-2 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-300"
                      data-oid="nmg8h6b"
                    />

                    <div className="text-xs text-gray-500" data-oid="z6tjnmd">
                      Upload an image to use as style reference. The AI will
                      create icons matching the style of your reference image.
                    </div>
                    {imagePreview && (
                      <div className="mt-3" data-oid="44m5z.s">
                        <label
                          className="block text-sm font-medium text-gray-900 mb-2"
                          data-oid="9po_9p_"
                        >
                          Preview:
                        </label>
                        <div
                          className="border rounded p-2 bg-gray-50 flex items-center space-x-3"
                          data-oid="5g3q0a2"
                        >
                          <img
                            src={imagePreview}
                            alt="Reference preview"
                            className="h-20 w-20 object-cover rounded"
                            data-oid="vszm7t."
                          />

                          <div className="flex-1" data-oid="zifj0__">
                            <p
                              className="text-sm text-gray-600"
                              data-oid="ore_36n"
                            >
                              {referenceImage?.name}
                            </p>
                            <p
                              className="text-xs text-gray-400"
                              data-oid="4ibjt27"
                            >
                              {referenceImage
                                ? formatFileSize(referenceImage.size)
                                : ""}
                            </p>
                          </div>
                          <button
                            type="button"
                            onClick={removeImage}
                            className="text-red-500 hover:text-red-700 text-sm"
                            data-oid=":hssy_d"
                          >
                            Remove
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>

            

            <div data-oid="nd7t0dj">
              <label
                className="block text-sm font-medium text-gray-900 mb-3"
                data-oid="-6l7ck_"
              >
                Individual Icon Descriptions (Optional)
              </label>
              {renderIconFields()}
            </div>

            <button
              type="submit"
              disabled={isGenerating}
              className={`w-full py-4 px-6 rounded-2xl text-white font-semibold ${isGenerating ? "bg-slate-400 cursor-not-allowed" : "bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 transform hover:scale-[1.02] shadow-lg hover:shadow-xl"} transition-all duration-200`}
              data-oid="c1kv6ls"
            >
              {isGenerating ? (
                <div
                  className="flex items-center justify-center space-x-2"
                  data-oid="wr:qqx5"
                >
                  <div
                    className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"
                    data-oid="klkpq:y"
                  ></div>
                  <span data-oid="-o8k1u8">Generating...</span>
                </div>
              ) : (
                <div className="flex items-center justify-center space-x-2">
                  <span>Generate Icons</span>
                  <span className="flex items-center space-x-1 rounded-full bg-white/20 px-2 py-0.5 text-xs font-semibold">
                    <Image src="/images/coin.webp" alt="Coins" width={16} height={16} />
                    <span>1</span>
                  </span>
                </div>
              )}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default GeneratorForm;
