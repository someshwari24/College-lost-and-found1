function showMessage(text, ok = true) {
    const box = document.getElementById("message");

    if (!box) {
        alert(text);
        return;
    }

    box.className = `message ${ok ? "success" : "error"}`;
    box.textContent = text;
}

async function registerUser(event) {

    event.preventDefault();

    const form = event.target;
    const button = form.querySelector("button[type='submit']");

    const formData = new FormData(form);

    const data = {
        name: String(formData.get("name") || "").trim(),
        email: String(formData.get("email") || "").trim(),
        password: String(formData.get("password") || ""),
        department: String(formData.get("department") || "").trim(),
        phone: String(formData.get("phone") || "").trim()
    };

    console.log("Registration Data:", {
        ...data,
        password: "[hidden]",
        passwordLength: data.password.length
    });

    if (!data.name) {
        showMessage("Full name is required.", false);
        return;
    }

    if (!data.email) {
        showMessage("Email is required.", false);
        return;
    }

    if (data.password.length < 6) {
        showMessage(
            "Password must have at least 6 characters.",
            false
        );
        return;
    }

    if (!data.department) {
        showMessage("Department is required.", false);
        return;
    }

    if (!/^\d{10}$/.test(data.phone)) {
        showMessage(
            "Phone number must contain exactly 10 digits.",
            false
        );
        return;
    }

    if (typeof API_BASE_URL === "undefined" || !API_BASE_URL) {
        showMessage(
            "API_BASE_URL is not configured.",
            false
        );
        return;
    }

    if (button) {
        button.disabled = true;
        button.textContent = "Registering...";
    }

    showMessage("Connecting to server...", true);

    try {

        console.log(
            "Register URL:",
            `${API_BASE_URL}/register`
        );

        const response = await fetch(
            `${API_BASE_URL}/register`,
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(data)
            }
        );

        const output = await response.json();

        console.log("Registration Response:", output);

        showMessage(
            output.message || "Registration successful.",
            response.ok
        );

        if (response.ok) {

            form.reset();

            setTimeout(() => {
                window.location.href = "login.html";
            }, 1000);

        }

    } catch (error) {

        console.error(error);

        showMessage(
            "Unable to connect to the backend.",
            false
        );

    } finally {

        if (button) {
            button.disabled = false;
            button.textContent = "Register";
        }

    }
}

async function loginUser(event) {

    event.preventDefault();

    const form = event.target;
    const button = form.querySelector("button[type='submit']");

    const data = {
        email: form.email.value.trim(),
        password: form.password.value
    };

    if (typeof API_BASE_URL === "undefined" || !API_BASE_URL) {
        showMessage(
            "API_BASE_URL is not configured.",
            false
        );
        return;
    }

    if (button) {
        button.disabled = true;
        button.textContent = "Logging in...";
    }

    showMessage("Connecting to server...", true);

    try {

        const response = await fetch(
            `${API_BASE_URL}/login`,
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(data)
            }
        );

        const output = await response.json();

        console.log("Login Response:", output);

        if (!response.ok) {
            throw new Error(
                output.message || "Login failed."
            );
        }

        localStorage.setItem(
            "user",
            JSON.stringify(output)
        );

        showMessage(
            output.message || "Login successful.",
            true
        );

        setTimeout(() => {

            window.location.href =
                "student-dashboard.html";

        }, 500);

    } catch (error) {

        console.error(error);

        showMessage(
            error.message,
            false
        );

    } finally {

        if (button) {
            button.disabled = false;
            button.textContent = "Login";
        }

    }
}

function getLoggedInUser() {

    const user =
        localStorage.getItem("user");

    return user
        ? JSON.parse(user)
        : null;
}

function requireLogin() {

    const user =
        getLoggedInUser();

    if (!user) {

        window.location.href =
            "login.html";

        return null;
    }

    return user;
}

function logout() {

    localStorage.removeItem("user");

    window.location.href =
        "login.html";
}