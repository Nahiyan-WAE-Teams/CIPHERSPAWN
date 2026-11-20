import test from "node:test";
import assert from "node:assert/strict";
import { parseChannelInput } from "../src/index.js";

test("parses a bare YouTube handle", () => {
  assert.deepEqual(parseChannelInput("@GoogleDevelopers"), { handle: "GoogleDevelopers" });
});

test("parses a channel handle URL", () => {
  assert.deepEqual(parseChannelInput("https://www.youtube.com/@GoogleDevelopers"), { handle: "GoogleDevelopers" });
});

test("parses a channel ID URL", () => {
  const id = "UC_x5XG1OV2P6uZZ5FSM9Ttw";
  assert.deepEqual(parseChannelInput(`https://www.youtube.com/channel/${id}`), { id });
});

test("rejects unsupported legacy custom URLs instead of guessing", () => {
  assert.throws(() => parseChannelInput("https://www.youtube.com/c/SomeCreator"), /Legacy custom URLs/);
});
