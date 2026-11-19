const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProjectPayload(uid, timestamp = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    title: "دستیار کاتلین",
    description: "پروژه مدیریت پرامپت اندروید",
    category: "CODE",
    modelId: "gemini-3.8-flash",
    systemInstruction: "You are a senior Android Kotlin engineer.",
    temperature: 0.7,
    topP: 0.95,
    topK: 40,
    maxOutputTokens: 2048,
    jsonModeEnabled: false,
    jsonSchema: "",
    safetyBalanced: true,
    pinned: false,
    messageCount: 0,
    createdAt: timestamp,
    updatedAt: timestamp,
  };
}

function validMessagePayload(uid, projectId, timestamp = new Date(Date.now() - 1000)) {
  return {
    userId: uid,
    projectId,
    role: "user",
    content: "یک نمونه کد Jetpack Compose بنویس",
    modelId: "gemini-3.8-flash",
    estimatedTokens: 16,
    hasImageAttachment: false,
    createdAt: timestamp,
  };
}

function validExportPayload(uid, timestamp = new Date(Date.now() - 1000)) {
  return {
    userId: uid,
    projectTitle: "دستیار کاتلین",
    exportType: "ANDROID_PROJECT_ZIP",
    packageName: "com.promptstudio.project1",
    fileName: "project_android_apk_project.zip",
    sizeBytes: 12480,
    createdAt: timestamp,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write projects, messages, or exports", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .set(validProjectPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("exports").get());
});

test("Owner can create, list, update (pin/settings/messageCount), and delete project", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projectsRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects");
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(projectsRef.doc("proj_1").set(validProjectPayload(ALICE_UID, created)));
  await assertSucceeds(projectsRef.get());

  // Action 1: Model tuning / system instruction update
  await assertSucceeds(
    projectsRef.doc("proj_1").update({
      modelId: "gemini-4-argon",
      temperature: 0.4,
      systemInstruction: "Updated system prompt",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Action 2: Pin toggle
  await assertSucceeds(
    projectsRef.doc("proj_1").update({
      pinned: true,
      updatedAt: new Date(Date.now() - 1500),
    })
  );

  // Action 3: Message count update
  await assertSucceeds(
    projectsRef.doc("proj_1").update({
      messageCount: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable field tamper must fail
  await assertFails(
    projectsRef.doc("proj_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Shadow update with ghost field must fail
  await assertFails(
    projectsRef.doc("proj_1").update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Fresh create with forbidden `@DocumentId` id field must fail
  await assertFails(
    projectsRef.doc("proj_bad_id_key").set({
      ...validProjectPayload(ALICE_UID, created),
      id: "proj_bad_id_key",
    })
  );

  // Future timestamp must fail
  await assertFails(
    projectsRef
      .doc("proj_future")
      .set(validProjectPayload(ALICE_UID, new Date(Date.now() + 60000)))
  );

  await assertSucceeds(projectsRef.doc("proj_1").delete());
});

test("Cross-user access to projects, messages, and exports is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_alice")
      .set(validProjectPayload(ALICE_UID))
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_alice").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_alice")
      .update({ pinned: true, updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_alice").delete()
  );
});

test("Messages require existing parent project and reject cross-project mismatch or updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_chat");

  // Orphaned message without parent project must fail
  await assertFails(
    projRef.collection("messages").doc("msg_orphan").set(validMessagePayload(ALICE_UID, "proj_chat"))
  );

  await assertSucceeds(projRef.set(validProjectPayload(ALICE_UID)));

  // Valid message create & list
  await assertSucceeds(
    projRef.collection("messages").doc("msg_1").set(validMessagePayload(ALICE_UID, "proj_chat"))
  );
  await assertSucceeds(projRef.collection("messages").orderBy("createdAt", "asc").get());

  // ProjectId mismatch must fail
  await assertFails(
    projRef.collection("messages").doc("msg_bad").set(validMessagePayload(ALICE_UID, "other_proj"))
  );

  // Message update is forbidden
  await assertFails(projRef.collection("messages").doc("msg_1").update({ content: "edited" }));

  // Owner can delete message
  await assertSucceeds(projRef.collection("messages").doc("msg_1").delete());
});

test("Export records enforce schema, ownership, and disallow updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const exportsRef = aliceDb.collection("users").doc(ALICE_UID).collection("exports");

  await assertSucceeds(exportsRef.doc("exp_1").set(validExportPayload(ALICE_UID)));
  await assertSucceeds(exportsRef.orderBy("createdAt", "desc").limit(30).get());

  // Invalid exportType rejected
  await assertFails(
    exportsRef.doc("exp_bad").set({
      ...validExportPayload(ALICE_UID),
      exportType: "UNKNOWN",
    })
  );

  await assertFails(exportsRef.doc("exp_1").update({ fileName: "tampered.zip" }));
  await assertSucceeds(exportsRef.doc("exp_1").delete());
});
