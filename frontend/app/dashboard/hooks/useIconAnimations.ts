import { useCallback, useEffect, useRef, useState } from "react";

type AnimatingIconsState = {
  [key: string]: number;
};

type AnimationTimersMap = {
  [key: string]: NodeJS.Timeout[];
};

export function useIconAnimations() {
  const [animatingIcons, setAnimatingIcons] = useState<AnimatingIconsState>({});
  const animationTimersRef = useRef<AnimationTimersMap>({});

  const clearIconAnimation = useCallback((serviceId: string) => {
    const timers = animationTimersRef.current[serviceId];
    if (timers) {
      timers.forEach((timer) => clearTimeout(timer));
      delete animationTimersRef.current[serviceId];
    }
    setAnimatingIcons((prev) => {
      if (!(serviceId in prev)) {
        return prev;
      }
      const updated = { ...prev };
      delete updated[serviceId];
      return updated;
    });
  }, []);

  const clearAllAnimations = useCallback(() => {
    Object.keys(animationTimersRef.current).forEach((serviceId) => {
      clearIconAnimation(serviceId);
    });
  }, [clearIconAnimation]);

  const startIconAnimation = useCallback(
    (serviceId: string, iconCount: number) => {
      clearIconAnimation(serviceId);
      setAnimatingIcons((prev) => ({ ...prev, [serviceId]: 0 }));
      const timers: NodeJS.Timeout[] = [];
      for (let i = 0; i < iconCount; i++) {
        const timer = setTimeout(() => {
          setAnimatingIcons((prev) => ({
            ...prev,
            [serviceId]: i + 1,
          }));
        }, i * 150);
        timers.push(timer);
      }
      animationTimersRef.current[serviceId] = timers;
    },
    [clearIconAnimation],
  );

  const getIconAnimationClass = useCallback(
    (serviceId: string, iconIndex: number) => {
      const visibleCount = animatingIcons[serviceId] || 0;
      const isVisible = iconIndex < visibleCount;
      return isVisible
        ? "opacity-100 scale-100 transition-all duration-500 ease-out"
        : "opacity-0 scale-75 transition-all duration-500 ease-out";
    },
    [animatingIcons],
  );

  useEffect(() => {
    return () => {
      clearAllAnimations();
    };
  }, [clearAllAnimations]);

  return {
    animatingIcons,
    setAnimatingIcons,
    startIconAnimation,
    clearIconAnimation,
    clearAllAnimations,
    getIconAnimationClass,
  };
}

export type IconAnimationController = ReturnType<typeof useIconAnimations>;
