// The Android/App build shares the H5 renderer but has its own optional layout override.
export const layoutPlatform = import.meta.env.VITE_NATIVE_APP === 'true' ? 'app' : 'h5'
