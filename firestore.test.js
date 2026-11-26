const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_exec_123";
const BOB_UID = "bob_exec_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function validPlanPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 5000);
  return {
    userId: uid,
    name: "Enterprise Cloud",
    tierCode: "ENTERPRISE",
    baseMonthlyCents: 149900,
    perSeatMonthlyCents: 2900,
    includedSeats: 25,
    annualDiscountPct: 20,
    apiCallsLimitK: 5000,
    slaSupport: "DEDICATED_24_7",
    active: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validAccountPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 5000);
  return {
    userId: uid,
    companyName: "Acme Robotics Inc.",
    industry: "Industrial Automation",
    contactName: "Elena Vance",
    contactEmail: "elena@acmerobotics.io",
    planId: "plan_ent_1",
    planName: "Enterprise Cloud",
    tierCode: "ENTERPRISE",
    billingCycle: "ANNUAL",
    licensedSeats: 40,
    activeSeats: 34,
    mrrCents: 193400,
    status: "ACTIVE",
    health: "HEALTHY",
    notes: "Key Q4 expansion account.",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validInvoicePayload(uid, accountId, overrides = {}) {
  const now = new Date(Date.now() - 5000);
  return {
    userId: uid,
    accountId,
    companyName: "Acme Robotics Inc.",
    invoiceNumber: "INV-2026-1001",
    billingPeriod: "Nov 2026",
    amountCents: 193400,
    status: "SENT",
    dueDateLabel: "2026-12-15",
    notes: "Annual enterprise installment",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated user: denied across plans, accounts, and invoices", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/plans`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/accounts`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/invoices`).get());
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/accounts`)
      .doc("acc_1")
      .set(validAccountPayload(ALICE_UID))
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's workspace", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection(`users/${ALICE_UID}/plans`).doc("plan_1").set(validPlanPayload(ALICE_UID));
    await db.collection(`users/${ALICE_UID}/accounts`).doc("acc_1").set(validAccountPayload(ALICE_UID));
    await db
      .collection(`users/${ALICE_UID}/invoices`)
      .doc("inv_1")
      .set(validInvoicePayload(ALICE_UID, "acc_1"));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/plans`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/accounts`).doc("acc_1").get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/invoices`).doc("inv_1").get());
  await assertFails(
    bobDb
      .collection(`users/${ALICE_UID}/accounts`)
      .doc("acc_1")
      .update({ companyName: "Hijacked Corp", updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/accounts`).doc("acc_1").delete());
});

test("SaasPlan: Kotlin create, partial updates, ghost field rejection, and active toggle", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const plansRef = aliceDb.collection(`users/${ALICE_UID}/plans`);

  await assertSucceeds(plansRef.doc("plan_ent_1").set(validPlanPayload(ALICE_UID)));
  await assertSucceeds(plansRef.get());

  // Toggle active status
  await assertSucceeds(
    plansRef.doc("plan_ent_1").update({
      active: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field / @DocumentId 'id' leakage
  await assertFails(
    plansRef.doc("plan_ghost").set(validPlanPayload(ALICE_UID, { id: "plan_ghost" }))
  );

  // Reject future timestamp spoofing
  await assertFails(
    plansRef
      .doc("plan_future")
      .set(validPlanPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  // Reject immutable createdAt modification
  await assertFails(
    plansRef.doc("plan_ent_1").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("B2bAccount: create, partial seat/status updates, optional field omission, and validation bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const accountsRef = aliceDb.collection(`users/${ALICE_UID}/accounts`);

  // Create without optional planId / notes (matches Kotlin .filterValues { it != null })
  const minimal = validAccountPayload(ALICE_UID);
  delete minimal.planId;
  delete minimal.notes;
  await assertSucceeds(accountsRef.doc("acc_1").set(minimal));

  // Partial seat & MRR adjustment
  await assertSucceeds(
    accountsRef.doc("acc_1").update({
      licensedSeats: 55,
      activeSeats: 48,
      mrrCents: 236900,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Partial status & health update
  await assertSucceeds(
    accountsRef.doc("acc_1").update({
      status: "ACTIVE",
      health: "EXPANDING",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid tierCode enum
  await assertFails(
    accountsRef.doc("acc_bad").set(validAccountPayload(ALICE_UID, { tierCode: "FREE_FOREVER" }))
  );

  // Reject userId spoofing
  await assertFails(
    accountsRef.doc("acc_spoof").set(validAccountPayload(BOB_UID))
  );
});

test("SaasInvoice: parent account existence check and terminal PAID/VOID state lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const accountsRef = aliceDb.collection(`users/${ALICE_UID}/accounts`);
  const invoicesRef = aliceDb.collection(`users/${ALICE_UID}/invoices`);

  // Orphaned invoice creation must fail when parent account doesn't exist
  await assertFails(
    invoicesRef.doc("inv_orphan").set(validInvoicePayload(ALICE_UID, "missing_acc"))
  );

  // Create parent account first
  await assertSucceeds(accountsRef.doc("acc_1").set(validAccountPayload(ALICE_UID)));

  // Now invoice creation succeeds
  await assertSucceeds(
    invoicesRef.doc("inv_1").set(validInvoicePayload(ALICE_UID, "acc_1"))
  );

  // Transition SENT -> PAID succeeds
  await assertSucceeds(
    invoicesRef.doc("inv_1").update({
      status: "PAID",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: cannot mutate PAID invoice afterwards
  await assertFails(
    invoicesRef.doc("inv_1").update({
      status: "DRAFT",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Also test VOID terminal lock
  await assertSucceeds(
    invoicesRef.doc("inv_2").set(validInvoicePayload(ALICE_UID, "acc_1", { status: "DRAFT" }))
  );
  await assertSucceeds(
    invoicesRef.doc("inv_2").update({
      status: "VOID",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    invoicesRef.doc("inv_2").update({
      amountCents: 100,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
