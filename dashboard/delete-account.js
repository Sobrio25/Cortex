import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import {
  GoogleAuthProvider,
  browserLocalPersistence,
  getAuth,
  getIdToken,
  onAuthStateChanged,
  setPersistence,
  signInWithPopup,
  signOut,
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";

const signInButton = document.querySelector("#sign-in");
const deleteButton = document.querySelector("#delete");
const confirmInput = document.querySelector("#confirm");
const status = document.querySelector("#status");
let auth;
let currentUser;

function update() {
  signInButton.textContent = currentUser ? `Cuenta: ${currentUser.email || "Google"}` : "Entrar con Google";
  deleteButton.disabled = !currentUser || !confirmInput.checked;
}

async function boot() {
  try {
    const configResponse = await fetch("/__/firebase/init.json", { cache: "no-store" });
    if (!configResponse.ok) throw new Error("No se pudo iniciar Firebase.");
    auth = getAuth(initializeApp(await configResponse.json()));
    await setPersistence(auth, browserLocalPersistence);
    onAuthStateChanged(auth, (user) => {
      currentUser = user;
      status.textContent = user ? "Cuenta verificada. Confirma para continuar." : "";
      update();
    });
  } catch (error) {
    status.textContent = error instanceof Error ? error.message : "No se pudo iniciar el proceso.";
  }
}

signInButton.addEventListener("click", async () => {
  if (currentUser) return;
  status.textContent = "Abriendo inicio de sesión…";
  try {
    await signInWithPopup(auth, new GoogleAuthProvider());
  } catch (error) {
    status.textContent = error instanceof Error ? error.message : "No se pudo iniciar sesión.";
  }
});

confirmInput.addEventListener("change", update);

deleteButton.addEventListener("click", async () => {
  if (!currentUser || !confirmInput.checked) return;
  deleteButton.disabled = true;
  status.textContent = "Eliminando cuenta y datos…";
  try {
    const token = await getIdToken(currentUser, true);
    const response = await fetch("/v1/account", {
      method: "DELETE",
      headers: { Authorization: `Bearer ${token}`, Accept: "application/json" },
    });
    if (!response.ok) {
      const payload = await response.json().catch(() => ({}));
      throw new Error(payload.error || "No se pudo eliminar la cuenta.");
    }
    await signOut(auth).catch(() => undefined);
    currentUser = undefined;
    confirmInput.checked = false;
    status.textContent = "Tu cuenta de Cortex y los datos vinculados fueron eliminados.";
    update();
  } catch (error) {
    status.textContent = error instanceof Error ? error.message : "No se pudo eliminar la cuenta.";
    update();
  }
});

boot();
