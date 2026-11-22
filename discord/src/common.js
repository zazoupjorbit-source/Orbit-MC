// Config, état et utilitaires partagés entre setup.js et bot.js.
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
require('dotenv').config({ path: path.join(ROOT, '.env'), quiet: true });

const STATE_FILE = path.join(ROOT, 'data', 'state.json');

function loadConfig() {
  return JSON.parse(fs.readFileSync(path.join(ROOT, 'config.json'), 'utf8'));
}

function loadState() {
  try {
    return { roles: {}, channels: {}, messages: {}, ...JSON.parse(fs.readFileSync(STATE_FILE, 'utf8')) };
  } catch {
    return { roles: {}, channels: {}, messages: {} };
  }
}

function saveState(state) {
  fs.mkdirSync(path.dirname(STATE_FILE), { recursive: true });
  fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
}

function requireEnv() {
  const { DISCORD_TOKEN, GUILD_ID } = process.env;
  if (!DISCORD_TOKEN || !GUILD_ID || DISCORD_TOKEN.startsWith('colle')) {
    console.error('✖ Remplis DISCORD_TOKEN et GUILD_ID dans le fichier .env (voir .env.example).');
    process.exit(1);
  }
  return { token: DISCORD_TOKEN, guildId: GUILD_ID };
}

module.exports = { loadConfig, loadState, saveState, requireEnv };
