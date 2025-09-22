module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', 'karma-typescript'],
    files: [
      { pattern: 'src/**/*.spec.ts' },
      { pattern: 'src/**/*.ts' }
    ],
    preprocessors: {
      'src/**/*.ts': ['karma-typescript']
    },
    reporters: ['progress', 'karma-typescript'],
    browsers: ['ChromeHeadless'],
    singleRun: true,
    concurrency: Infinity
  });
};
