import type { PluginOption } from 'vite';

export default function configAppVersionPlugin(version: string): PluginOption {
  return {
    name: 'easy-store-app-version',
    apply: 'build',
    generateBundle() {
      this.emitFile({
        type: 'asset',
        fileName: 'version.json',
        source: JSON.stringify(
          {
            version: version || '0.0.0',
            buildTime: new Date().toISOString(),
          },
          null,
          2
        ),
      });
    },
  };
}
