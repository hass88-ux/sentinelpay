import {build} from 'vite';
import {buildPolicies} from './build-policies.mjs';
await build();
await buildPolicies();
