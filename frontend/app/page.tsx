'use client';

import { useState } from 'react';

export default function Page() {
    const [inputType, setInputType] = useState('text');
    const [iconCount, setIconCount] = useState('9');

    const renderIconFields = () => {
        const count = parseInt(iconCount);
        const rows = count === 9 ? 3 : 6;
        const fields = [];

        for (let i = 0; i < count; i++) {
            fields.push(
                <input
                    key={i}
                    type="text"
                    placeholder={`Icon ${i + 1}`}
                    className="w-full px-3 py-2 border border-gray-200 rounded-md text-sm focus:outline-none focus:ring-1 focus:ring-gray-300"
                    data-oid="9uf-jiz"
                />,
            );
        }

        return (
            <div className="grid grid-cols-3 gap-3" data-oid="30i.tv8">
                {fields}
            </div>
        );
    };

    return (
        <div className="min-h-screen bg-white" data-oid="b5sohb5">
            {/* Navigation */}
            <nav className="border-b border-gray-200 px-6 py-4" data-oid="zknb.vl">
                <div className="flex items-center justify-between" data-oid="n-8.7xb">
                    <div className="flex items-center space-x-3" data-oid="cfnkurh">
                        <div
                            className="w-8 h-8 bg-black rounded-lg flex items-center justify-center"
                            data-oid="af4jp5e"
                        >
                            <svg
                                className="w-5 h-5 text-white"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                                data-oid="o.r04n7"
                            >
                                <path
                                    fillRule="evenodd"
                                    d="M3 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z"
                                    clipRule="evenodd"
                                    data-oid="9mtht3s"
                                />
                            </svg>
                        </div>
                        <span className="text-xl font-medium text-black" data-oid="p8a78sv">
                            Icon Pack Generator
                        </span>
                    </div>

                    <div className="flex items-center space-x-4" data-oid="yorvl9x">
                        {/* Dashboard */}
                        <button className="p-2 hover:bg-gray-100 rounded-lg" data-oid="7vggw9v">
                            <svg
                                className="w-5 h-5 text-gray-700"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                                data-oid="4akwqht"
                            >
                                <path
                                    d="M3 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z"
                                    data-oid="25z35-_"
                                />
                            </svg>
                        </button>

                        {/* Gallery */}
                        <button className="p-2 hover:bg-gray-100 rounded-lg" data-oid="jmagbl2">
                            <svg
                                className="w-5 h-5 text-gray-700"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                                data-oid="dhm6h6n"
                            >
                                <path
                                    fillRule="evenodd"
                                    d="M4 3a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V5a2 2 0 00-2-2H4zm12 12H4l4-8 3 6 2-4 3 6z"
                                    clipRule="evenodd"
                                    data-oid="bsrvjm2"
                                />
                            </svg>
                        </button>

                        {/* Store */}
                        <button className="p-2 hover:bg-gray-100 rounded-lg" data-oid="5hv4en:">
                            <svg
                                className="w-5 h-5 text-gray-700"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                                data-oid="87k07jv"
                            >
                                <path
                                    fillRule="evenodd"
                                    d="M10 2L3 7v11a2 2 0 002 2h10a2 2 0 002-2V7l-7-5zM8 15v-3a1 1 0 011-1h2a1 1 0 011 1v3H8z"
                                    clipRule="evenodd"
                                    data-oid="4qc_056"
                                />
                            </svg>
                        </button>

                        {/* Feedback */}
                        <button className="p-2 hover:bg-gray-100 rounded-lg" data-oid="vu:..ja">
                            <svg
                                className="w-5 h-5 text-gray-700"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                                data-oid=".gjp81p"
                            >
                                <path
                                    fillRule="evenodd"
                                    d="M18 10c0 3.866-3.582 7-8 7a8.841 8.841 0 01-4.083-.98L2 17l1.338-3.123C2.493 12.767 2 11.434 2 10c0-3.866 3.582-7 8-7s8 3.134 8 7zM7 9H5v2h2V9zm8 0h-2v2h2V9zM9 9h2v2H9V9z"
                                    clipRule="evenodd"
                                    data-oid="soz-cvx"
                                />
                            </svg>
                        </button>

                        {/* Settings */}
                        <button className="p-2 hover:bg-gray-100 rounded-lg" data-oid="55iot5k">
                            <svg
                                className="w-5 h-5 text-gray-700"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                                data-oid="jiff5tr"
                            >
                                <path
                                    fillRule="evenodd"
                                    d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947zM10 13a3 3 0 100-6 3 3 0 000 6z"
                                    clipRule="evenodd"
                                    data-oid="xeh8yfi"
                                />
                            </svg>
                        </button>

                        {/* Logout */}
                        <button className="p-2 hover:bg-gray-100 rounded-lg" data-oid="_7h97iu">
                            <svg
                                className="w-5 h-5 text-gray-700"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                                data-oid="dh5t5m:"
                            >
                                <path
                                    fillRule="evenodd"
                                    d="M3 3a1 1 0 00-1 1v12a1 1 0 102 0V4a1 1 0 00-1-1zm10.293 9.293a1 1 0 001.414 1.414l3-3a1 1 0 000-1.414l-3-3a1 1 0 10-1.414 1.414L14.586 9H7a1 1 0 100 2h7.586l-1.293 1.293z"
                                    clipRule="evenodd"
                                    data-oid="ms_67.o"
                                />
                            </svg>
                        </button>
                    </div>
                </div>
            </nav>

            <div className="flex h-screen bg-gray-100" data-oid="ce_dil_">
                {/* Sidebar */}
                <div className="w-1/3 p-6" data-oid="wjiv3vt">
                    <div
                        className="bg-white rounded-2xl p-6 shadow-sm h-full overflow-y-auto"
                        data-oid="3.4sx_d"
                    >
                        <div className="space-y-6" data-oid="o5b.dy_">
                            {/* Choose Input Type */}
                            <div data-oid="5bv_l8m">
                                <label
                                    className="block text-sm font-medium text-gray-900 mb-4"
                                    data-oid="9vudxob"
                                >
                                    Choose input type
                                </label>
                                <div className="bg-gray-100 p-1 rounded-xl flex" data-oid="uu4m80l">
                                    <button
                                        type="button"
                                        onClick={() => setInputType('text')}
                                        className={`flex-1 px-4 py-3 rounded-lg text-sm font-medium transition-all duration-200 flex items-center justify-center space-x-2 ${
                                            inputType === 'text'
                                                ? 'bg-white text-gray-900 shadow-sm'
                                                : 'text-gray-600 hover:text-gray-900'
                                        }`}
                                        data-oid="2:-vlo0"
                                    >
                                        <svg
                                            className="w-4 h-4"
                                            fill="none"
                                            stroke="currentColor"
                                            viewBox="0 0 24 24"
                                            data-oid="bwsq3z1"
                                        >
                                            <path
                                                strokeLinecap="round"
                                                strokeLinejoin="round"
                                                strokeWidth={2}
                                                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                                                data-oid="juy.wky"
                                            />
                                        </svg>
                                        <span data-oid="6o.1n8e">Text Description</span>
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setInputType('image')}
                                        className={`flex-1 px-4 py-3 rounded-lg text-sm font-medium transition-all duration-200 flex items-center justify-center space-x-2 ${
                                            inputType === 'image'
                                                ? 'bg-white text-gray-900 shadow-sm'
                                                : 'text-gray-600 hover:text-gray-900'
                                        }`}
                                        data-oid="ey7c50:"
                                    >
                                        <svg
                                            className="w-4 h-4"
                                            fill="none"
                                            stroke="currentColor"
                                            viewBox="0 0 24 24"
                                            data-oid="qm_vgke"
                                        >
                                            <path
                                                strokeLinecap="round"
                                                strokeLinejoin="round"
                                                strokeWidth={2}
                                                d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                                                data-oid="kasr2wx"
                                            />
                                        </svg>
                                        <span data-oid="hbmn29u">Reference Image</span>
                                    </button>
                                </div>
                            </div>

                            {/* Input Section */}
                            <div data-oid="zp.j8i3">
                                {inputType === 'text' ? (
                                    <div data-oid="df66i8b">
                                        <label
                                            className="block text-sm font-medium text-gray-900 mb-2"
                                            data-oid="n6nxnnm"
                                        >
                                            General Theme Description
                                        </label>
                                        <textarea
                                            rows={4}
                                            className="w-full px-3 py-2 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-300"
                                            placeholder="Describe the general theme for your icon pack..."
                                            data-oid="qqqra:g"
                                        />
                                    </div>
                                ) : (
                                    <div data-oid="246ggpb">
                                        <label
                                            className="block text-sm font-medium text-gray-900 mb-2"
                                            data-oid="f4m15e_"
                                        >
                                            Reference Image
                                        </label>
                                        <div
                                            className="border-2 border-dashed border-gray-300 rounded-md p-6 text-center"
                                            data-oid="t1ecflz"
                                        >
                                            <svg
                                                className="mx-auto h-12 w-12 text-gray-400"
                                                stroke="currentColor"
                                                fill="none"
                                                viewBox="0 0 48 48"
                                                data-oid="4xqznke"
                                            >
                                                <path
                                                    d="M28 8H12a4 4 0 00-4 4v20m32-12v8m0 0v8a4 4 0 01-4 4H12a4 4 0 01-4-4v-4m32-4l-3.172-3.172a4 4 0 00-5.656 0L28 28M8 32l9.172-9.172a4 4 0 015.656 0L28 28m0 0l4 4m4-24h8m-4-4v8m-12 4h.02"
                                                    strokeWidth={2}
                                                    strokeLinecap="round"
                                                    strokeLinejoin="round"
                                                    data-oid="_iigwg9"
                                                />
                                            </svg>
                                            <div className="mt-2" data-oid="65kb26d">
                                                <button
                                                    className="text-sm text-gray-600 hover:text-gray-900"
                                                    data-oid="nthfiag"
                                                >
                                                    Click to upload or drag and drop
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                )}
                            </div>

                            {/* Icon Count */}
                            <div data-oid="kqm5iep">
                                <label
                                    className="block text-sm font-medium text-gray-900 mb-2"
                                    data-oid="qk23x2z"
                                >
                                    Number of Icons
                                </label>
                                <select
                                    value={iconCount}
                                    onChange={(e) => setIconCount(e.target.value)}
                                    className="w-full px-3 py-2 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-300"
                                    data-oid="xcc6u_j"
                                >
                                    <option value="9" data-oid="97nhg8j">
                                        9 Icons
                                    </option>
                                    <option value="18" data-oid="77fdeia">
                                        18 Icons
                                    </option>
                                </select>
                            </div>

                            {/* Individual Icon Descriptions */}
                            <div data-oid="b:8q06n">
                                <label
                                    className="block text-sm font-medium text-gray-900 mb-3"
                                    data-oid="3ric-ua"
                                >
                                    Individual Icon Descriptions (Optional)
                                </label>
                                {renderIconFields()}
                            </div>
                        </div>
                    </div>
                </div>

                {/* Main Content Area */}
                <div className="flex-1 p-6 flex space-x-6" data-oid="v8-sppr">
                    {/* Your Icons Component */}
                    <div className="bg-white rounded-2xl shadow-sm flex-1" data-oid="5n88ju1">
                        <div className="p-6 h-full flex flex-col" data-oid="hr6wy.y">
                            <div
                                className="flex items-center justify-between mb-4"
                                data-oid="a5bf8w2"
                            >
                                <h2
                                    className="text-lg font-medium text-gray-900"
                                    data-oid="7tu9u.x"
                                >
                                    Your Icons
                                </h2>
                                <button
                                    disabled
                                    className="px-4 py-2 bg-gray-100 text-gray-400 rounded-md text-sm cursor-not-allowed"
                                    data-oid="l6x98vg"
                                >
                                    Export Icons
                                </button>
                            </div>
                            <div
                                className="flex-1 bg-gray-50 rounded-lg border-2 border-dashed border-gray-200 flex items-center justify-center"
                                data-oid="d-2ggc6"
                            >
                                <p className="text-gray-500" data-oid="w1g6cr3">
                                    Generated icons will appear here
                                </p>
                            </div>
                        </div>
                    </div>

                    {/* Variations Component */}
                    <div className="bg-white rounded-2xl shadow-sm flex-1" data-oid="as-6w4f">
                        <div className="p-6 h-full flex flex-col" data-oid="u:rqzvb">
                            <div
                                className="flex items-center justify-between mb-4"
                                data-oid="039zeou"
                            >
                                <h2
                                    className="text-lg font-medium text-gray-900"
                                    data-oid="s95w:1y"
                                >
                                    Variations
                                </h2>
                                <button
                                    disabled
                                    className="px-4 py-2 bg-gray-100 text-gray-400 rounded-md text-sm cursor-not-allowed"
                                    data-oid="wvmv__:"
                                >
                                    Export Icons
                                </button>
                            </div>
                            <div
                                className="flex-1 bg-gray-50 rounded-lg border-2 border-dashed border-gray-200 flex items-center justify-center"
                                data-oid="8_bhpe4"
                            >
                                <p className="text-gray-500" data-oid="t99_0dj">
                                    Icon variations will appear here
                                </p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
