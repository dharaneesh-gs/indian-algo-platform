import { cp, mkdir, rm } from 'node:fs/promises';
import { resolve } from 'node:path';
const source = resolve('dist/web-client/browser');
const destination = resolve('../backend/src/main/resources/static');
await mkdir(destination, { recursive: true });
// The running backend serves the built Angular files from Spring Boot's static resources.
await rm(destination, { recursive: true, force: true });
await mkdir(destination, { recursive: true });
await cp(source, destination, { recursive: true });
console.log(`Angular web client copied to ${destination}`);
