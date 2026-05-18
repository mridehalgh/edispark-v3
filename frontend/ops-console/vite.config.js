import path from 'node:path';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';
var DEFAULT_BACKEND_ORIGIN = 'http://localhost:8080';
export default defineConfig(function (_a) {
    var mode = _a.mode;
    var env = loadEnv(mode, process.cwd(), '');
    var backendOrigin = env.VITE_BACKEND_ORIGIN || DEFAULT_BACKEND_ORIGIN;
    return {
        plugins: [react()],
        resolve: {
            alias: {
                '@': path.resolve(__dirname, './src'),
            },
        },
        server: {
            port: 5173,
            proxy: {
                '/api': backendOrigin,
                '/api-docs': backendOrigin,
                '/swagger-ui': backendOrigin,
                '/swagger-ui.html': backendOrigin,
            },
        },
        test: {
            environment: 'jsdom',
            setupFiles: './src/test/setup.ts',
            globals: true,
        },
    };
});
