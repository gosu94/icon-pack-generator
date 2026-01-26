import React, { useEffect, useRef, useState } from "react";

type CropRect = {
  x: number;
  y: number;
  width: number;
  height: number;
};

interface ImageCropperProps {
  src: string;
  crop: CropRect | null;
  onCropChange: (crop: CropRect | null) => void;
  disabled?: boolean;
}

const clamp = (value: number, min: number, max: number) =>
  Math.min(Math.max(value, min), max);

const ImageCropper: React.FC<ImageCropperProps> = ({
  src,
  crop,
  onCropChange,
  disabled = false,
}) => {
  const imageRef = useRef<HTMLImageElement | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [startPoint, setStartPoint] = useState<{ x: number; y: number } | null>(
    null,
  );
  const [draftCrop, setDraftCrop] = useState<CropRect | null>(null);

  const getRelativePoint = (event: React.PointerEvent) => {
    const rect = imageRef.current?.getBoundingClientRect();
    if (!rect) {
      return { x: 0, y: 0 };
    }
    const x = clamp(event.clientX - rect.left, 0, rect.width);
    const y = clamp(event.clientY - rect.top, 0, rect.height);
    return {
      x: rect.width ? x / rect.width : 0,
      y: rect.height ? y / rect.height : 0,
    };
  };

  const handlePointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (disabled) {
      return;
    }
    event.preventDefault();
    const point = getRelativePoint(event);
    setStartPoint(point);
    setDraftCrop({ x: point.x, y: point.y, width: 0, height: 0 });
    setIsDragging(true);
  };

  const handlePointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!isDragging || !startPoint) {
      return;
    }
    const point = getRelativePoint(event);
    const x = Math.min(startPoint.x, point.x);
    const y = Math.min(startPoint.y, point.y);
    const width = Math.abs(point.x - startPoint.x);
    const height = Math.abs(point.y - startPoint.y);
    setDraftCrop({ x, y, width, height });
  };

  const handlePointerUp = () => {
    if (!isDragging) {
      return;
    }
    setIsDragging(false);
    if (draftCrop && draftCrop.width > 0.01 && draftCrop.height > 0.01) {
      onCropChange(draftCrop);
    } else {
      onCropChange(null);
    }
    setDraftCrop(null);
    setStartPoint(null);
  };

  useEffect(() => {
    if (!isDragging) {
      setDraftCrop(null);
    }
  }, [isDragging]);

  const activeCrop = draftCrop || crop;

  return (
    <div className="relative w-full">
      <img
        ref={imageRef}
        src={src}
        alt="Crop reference"
        className="w-full rounded-lg border border-slate-200 object-contain"
      />
      <div
        className={`absolute inset-0 ${
          disabled ? "cursor-not-allowed" : "cursor-crosshair"
        }`}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerLeave={handlePointerUp}
      >
        {activeCrop && (
          <div
            className="absolute border-2 border-purple-500 bg-purple-200/20"
            style={{
              left: `${activeCrop.x * 100}%`,
              top: `${activeCrop.y * 100}%`,
              width: `${activeCrop.width * 100}%`,
              height: `${activeCrop.height * 100}%`,
            }}
          />
        )}
      </div>
    </div>
  );
};

export default ImageCropper;
