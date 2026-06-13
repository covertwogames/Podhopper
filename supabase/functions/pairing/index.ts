// PodHopper pairing Edge Function
// Deploy with: supabase functions deploy pairing --no-verify-jwt
// (JWT verification is handled manually for the approve action, because
// start and poll must be callable by an unauthenticated car.)

import { createClient } from "jsr:@supabase/supabase-js@2";

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const CODE_LENGTH = 6;
const CODE_TTL_MS = 10 * 60 * 1000;

function generateCode(): string {
  const bytes = new Uint8Array(CODE_LENGTH);
  crypto.getRandomValues(bytes);
  let code = "";
  for (const b of bytes) {
    code += CODE_ALPHABET[b % CODE_ALPHABET.length];
  }
  return code;
}

Deno.serve(async (req) => {
  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  let body;
  try {
    body = await req.json();
  } catch {
    return Response.json({ error: "invalid json" }, { status: 400 });
  }
  const action = body.action;

  if (action === "start") {
    const code = generateCode();
    const { error } = await admin.from("pairing_codes").insert({
      code,
      device_name: String(body.device_name ?? "").slice(0, 64),
      expires_at: new Date(Date.now() + CODE_TTL_MS).toISOString(),
    });
    if (error) {
      return Response.json({ error: error.message }, { status: 500 });
    }
    return Response.json({ code, expires_in: CODE_TTL_MS / 1000 });
  }

  if (action === "poll") {
    const code = String(body.code ?? "").toUpperCase();
    const { data, error } = await admin.from("pairing_codes")
      .select("token_hash, expires_at").eq("code", code).maybeSingle();
    if (error) {
      return Response.json({ error: error.message }, { status: 500 });
    }
    if (!data || new Date(data.expires_at) < new Date()) {
      return Response.json({ status: "expired" });
    }
    if (!data.token_hash) {
      return Response.json({ status: "pending" });
    }
    await admin.from("pairing_codes").delete().eq("code", code);
    return Response.json({ status: "approved", token_hash: data.token_hash });
  }

  if (action === "approve") {
    const authHeader = req.headers.get("Authorization") ?? "";
    const jwt = authHeader.replace("Bearer ", "");
    const { data: userData, error: userError } = await admin.auth.getUser(jwt);
    if (userError || !userData?.user?.email) {
      return Response.json({ error: "not authenticated" }, { status: 401 });
    }
    const code = String(body.code ?? "").toUpperCase();
    const { data, error } = await admin.from("pairing_codes")
      .select("code, expires_at, token_hash").eq("code", code).maybeSingle();
    if (error) {
      return Response.json({ error: error.message }, { status: 500 });
    }
    if (!data || data.token_hash || new Date(data.expires_at) < new Date()) {
      return Response.json({ error: "code not found or expired" }, { status: 404 });
    }
    const { data: linkData, error: linkError } = await admin.auth.admin.generateLink({
      type: "magiclink",
      email: userData.user.email,
    });
    if (linkError || !linkData?.properties?.hashed_token) {
      return Response.json({ error: linkError?.message ?? "link failed" }, { status: 500 });
    }
    const { error: updateError } = await admin.from("pairing_codes")
      .update({ token_hash: linkData.properties.hashed_token, approved_by: userData.user.id })
      .eq("code", code);
    if (updateError) {
      return Response.json({ error: updateError.message }, { status: 500 });
    }
    return Response.json({ status: "approved" });
  }

  return Response.json({ error: "unknown action" }, { status: 400 });
});
