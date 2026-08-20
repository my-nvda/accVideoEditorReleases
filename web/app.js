const API_BASE = "/api";

// State
let repos = [];
let activeRepoIndex = 0;
let currentTab = "cloud-config";
let allFeatures = [];
let activeDevices = [];
let localItems = [];
let remoteItems = [];
let selectedLocalIndex = -1;
let selectedRemoteIndex = -1;
let announcementsList = [];
let analyticsStats = [];
let crashesList = [];

// Initialize on DOM load
document.addEventListener("DOMContentLoaded", () => {
    initApp();
});

async function initApp() {
    await fetchFeatures();
    await fetchRepos();
    setupEventHandlers();
}

async function fetchFeatures() {
    try {
        const res = await fetch(`${API_BASE}/features`);
        const data = await res.json();
        allFeatures = data.features || [];
        populateFeaturesChecklist();
    } catch (e) {
        console.error("Failed fetching features", e);
    }
}

async function fetchRepos() {
    try {
        const res = await fetch(`${API_BASE}/repos`);
        const data = await res.json();
        repos = data.repos || [];
        activeRepoIndex = data.active_index || 0;
        
        const select = document.getElementById("repo-select");
        select.innerHTML = "";
        repos.forEach((repo, idx) => {
            const opt = document.createElement("option");
            opt.value = idx;
            opt.textContent = repo.name;
            if (idx === activeRepoIndex) opt.selected = true;
            select.appendChild(opt);
        });
        
        loadActiveRepoData();
    } catch (e) {
        console.error("Failed fetching repos", e);
    }
}

function loadActiveRepoData() {
    loadTabContent();
    refreshLists();
}

function loadTabContent() {
    if (currentTab === "cloud-config" || currentTab === "announcements") {
        fetchCloudConfig();
    } else if (currentTab === "app-update") {
        fetchAppUpdate();
    } else if (currentTab === "analytics") {
        fetchAnalytics();
    } else if (currentTab === "crashes") {
        fetchCrashes();
    }
}

// Fetch lists (files or tags) for sidebars
async function refreshLists() {
    const isTagMode = (currentTab === "tags-manager");
    const isSourceMode = (currentTab === "source-manager");
    const endpoint = isTagMode ? "tags" : "files";
    
    // Set sidebar headers
    const localHeader = document.getElementById("sidebar-local-title");
    const remoteHeader = document.getElementById("sidebar-remote-title");
    
    if (isTagMode) {
        localHeader.textContent = "الـ Tags المحلية (على جهازي):";
        remoteHeader.textContent = "الـ Tags المرفوعة (على GitHub):";
    } else if (isSourceMode) {
        localHeader.textContent = "ملفات الكود المحلية (على جهازي):";
        remoteHeader.textContent = "ملفات الكود على GitHub (السحاب):";
    } else {
        localHeader.textContent = "الملفات المحلية (على جهازي):";
        remoteHeader.textContent = "الملفات على GitHub (السحاب):";
    }
    
    try {
        const res = await fetch(`${API_BASE}/${endpoint}`);
        const data = await res.json();
        
        const localList = document.getElementById("list-local");
        const remoteList = document.getElementById("list-remote");
        
        localList.innerHTML = "";
        remoteList.innerHTML = "";
        selectedLocalIndex = -1;
        selectedRemoteIndex = -1;
        
        if (isTagMode) {
            localItems = data.local || [];
            remoteItems = data.remote || [];
            
            localItems.forEach((tag, idx) => {
                const li = document.createElement("li");
                li.textContent = tag;
                li.onclick = () => selectListItem("local", idx);
                localList.appendChild(li);
            });
            if (localItems.length === 0) {
                localList.innerHTML = "<li class='disabled'>لا توجد وسوم محلية</li>";
            }
            
            remoteItems.forEach((tag, idx) => {
                const li = document.createElement("li");
                li.textContent = tag;
                li.onclick = () => selectListItem("remote", idx);
                remoteList.appendChild(li);
            });
            if (remoteItems.length === 0) {
                remoteList.innerHTML = "<li class='disabled'>لا توجد وسوم على GitHub</li>";
            }
        } else {
            localItems = data.local || [];
            remoteItems = data.remote || [];
            
            localItems.forEach((file, idx) => {
                const li = document.createElement("li");
                const prefix = file.type === "folder" ? "📁 " : "📄 ";
                li.textContent = prefix + file.name;
                li.ondblclick = () => editFile(file.name);
                li.onclick = () => selectListItem("local", idx);
                localList.appendChild(li);
            });
            if (localItems.length === 0) {
                localList.innerHTML = "<li class='disabled'>المجلد فارغ</li>";
            }
            
            remoteItems.forEach((file, idx) => {
                const li = document.createElement("li");
                const prefix = file.type === "folder" ? "📁 " : "📄 ";
                li.textContent = prefix + file.name;
                li.onclick = () => selectListItem("remote", idx);
                remoteList.appendChild(li);
            });
            if (remoteItems.length === 0) {
                remoteList.innerHTML = `<li class='disabled'>المستودع فارغ على GitHub (الفرع ${data.branch || 'main'})</li>`;
            }
        }
    } catch (e) {
        console.error("Failed fetching lists", e);
    }
}

function selectListItem(type, idx) {
    const listId = type === "local" ? "list-local" : "list-remote";
    const ul = document.getElementById(listId);
    const lis = ul.getElementsByTagName("li");
    
    // Clear previous
    for (let li of lis) li.classList.remove("selected");
    
    if (type === "local") {
        selectedLocalIndex = idx;
        if (lis[idx]) lis[idx].classList.add("selected");
    } else {
        selectedRemoteIndex = idx;
        if (lis[idx]) lis[idx].classList.add("selected");
    }
}

// Fetch Cloud Config
async function fetchCloudConfig() {
    try {
        const res = await fetch(`${API_BASE}/cloud_config`);
        const data = await res.json();
        activeDevices = data.devices || [];
        announcementsList = data.announcements || [];
        
        const tokenInput = document.getElementById("github-token");
        const repoInput = document.getElementById("github-repo");
        if (tokenInput) tokenInput.value = data.github_token || "";
        if (repoInput) repoInput.value = data.github_repo || "";
        
        renderDevices();
        renderAnnouncements();
    } catch (e) {
        console.error("Failed fetching cloud config", e);
    }
}

function renderDevices() {
    const container = document.getElementById("device-list");
    container.innerHTML = "";
    
    activeDevices.forEach((dev, idx) => {
        const row = document.createElement("div");
        row.className = "device-row";
        
        row.innerHTML = `
            <div class="device-row-title">جهاز رقم ${idx + 1}:</div>
            <div class="form-group" style="flex: 1;">
                <input type="text" value="${dev.name}" placeholder="اسم صاحب الجهاز" onchange="updateDeviceField(${idx}, 'name', this.value)">
            </div>
            <div class="form-group" style="flex: 1.5;">
                <input type="text" value="${dev.id}" placeholder="معرّف الجهاز (ID)" onchange="updateDeviceField(${idx}, 'id', this.value)">
            </div>
            <button class="btn btn-secondary btn-small" onclick="openFeaturesSelection(${idx})">تحديد الميزات (${dev.features.length})</button>
            <button class="btn btn-danger btn-small" onclick="removeDeviceRow(${idx})">إزالة</button>
        `;
        container.appendChild(row);
    });
    
    if (activeDevices.length === 0) {
        container.innerHTML = "<p style='color: var(--text-secondary); text-align: center; padding: 20px;'>لا توجد أجهزة مضافة حالياً. اضغط إضافة جهاز جديد.</p>";
    }
}

function updateDeviceField(idx, field, value) {
    if (activeDevices[idx]) {
        activeDevices[idx][field] = value.trim();
    }
}

function removeDeviceRow(idx) {
    activeDevices.splice(idx, 1);
    renderDevices();
}

let activeFeaturesDeviceIndex = -1;
function openFeaturesSelection(idx) {
    activeFeaturesDeviceIndex = idx;
    const dev = activeDevices[idx];
    document.getElementById("features-modal-device-name").textContent = dev.name || "جهاز غير مسمى";
    
    // Set checked states
    allFeatures.forEach(feat => {
        const cb = document.getElementById(`cb-feat-${feat[0]}`);
        if (cb) {
            cb.checked = dev.features.includes(feat[0]);
        }
    });
    
    openModal("features-modal");
}

function populateFeaturesChecklist() {
    const grid = document.getElementById("modal-features-checklist");
    grid.innerHTML = "";
    allFeatures.forEach(feat => {
        const label = document.createElement("label");
        label.className = "feature-checkbox-label";
        label.innerHTML = `
            <input type="checkbox" id="cb-feat-${feat[0]}">
            <span>${feat[1]}</span>
        `;
        grid.appendChild(label);
    });
}

function saveFeaturesModalSelection() {
    if (activeFeaturesDeviceIndex === -1) return;
    const selected = [];
    allFeatures.forEach(feat => {
        const cb = document.getElementById(`cb-feat-${feat[0]}`);
        if (cb && cb.checked) {
            selected.push(feat[0]);
        }
    });
    activeDevices[activeFeaturesDeviceIndex].features = selected;
    closeModal("features-modal");
    renderDevices();
}

// Fetch App Update info
async function fetchAppUpdate() {
    try {
        const res = await fetch(`${API_BASE}/app_update`);
        const data = await res.json();
        document.getElementById("update-vc").value = data.versionCode || "";
        document.getElementById("update-vn").value = data.versionName || "";
        document.getElementById("update-url").value = data.downloadUrl || "";
        document.getElementById("update-notes").value = data.releaseNotes || "";
    } catch (e) {
        console.error("Failed fetching update info", e);
    }
}

// Switch tabs
window.switchTab = function(tabId) {
    currentTab = tabId;
    
    // Switch navigation button states
    const navButtons = document.querySelectorAll(".tab-btn");
    navButtons.forEach(btn => {
        if (btn.id === `tab-${tabId}`) {
            btn.classList.add("active");
        } else {
            btn.classList.remove("active");
        }
    });
    
    // Switch panels visibility
    const panels = document.querySelectorAll(".tab-panel");
    panels.forEach(panel => {
        if (panel.id === `panel-${tabId}`) {
            panel.classList.add("active");
        } else {
            panel.classList.remove("active");
        }
    });
    
    loadTabContent();
    refreshLists();
};

// Event handlers setup
function setupEventHandlers() {
    // Repo Selector change
    document.getElementById("repo-select").addEventListener("change", async (e) => {
        const idx = parseInt(e.target.value);
        await fetch(`${API_BASE}/active_repo`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ index: idx })
        });
        activeRepoIndex = idx;
        loadActiveRepoData();
    });
    
    // Repos Add/Edit/Delete
    document.getElementById("btn-add-repo").onclick = () => {
        document.getElementById("repo-modal-title").textContent = "إضافة مستودع جديد";
        document.getElementById("modal-repo-name").value = "";
        document.getElementById("modal-repo-url").value = "";
        document.getElementById("repo-url-group").style.display = "block";
        document.getElementById("repo-path-group").style.display = "none";
        
        document.getElementById("btn-repo-modal-submit").onclick = submitAddRepo;
        openModal("repo-modal");
    };
    
    document.getElementById("btn-edit-repo").onclick = () => {
        const repo = repos[activeRepoIndex];
        if (!repo) return;
        document.getElementById("repo-modal-title").textContent = "تعديل بيانات المستودع";
        document.getElementById("modal-repo-name").value = repo.name;
        document.getElementById("modal-repo-url").value = repo.url || "";
        document.getElementById("repo-url-group").style.display = "block";
        document.getElementById("repo-path-group").style.display = "block";
        document.getElementById("modal-repo-path").value = repo.path;
        
        document.getElementById("btn-repo-modal-submit").onclick = submitEditRepo;
        openModal("repo-modal");
    };
    
    document.getElementById("btn-delete-repo").onclick = () => {
        const repo = repos[activeRepoIndex];
        if (!repo) return;
        
        const deleteDisk = confirm(`هل أنت متأكد من رغبتك في إزالة المستودع '${repo.name}' من قائمة الأداة؟\nاضغط 'موافق' (OK) ثم أكد لو تود أيضاً مسح ملفاته بالكامل من القرص الصلب.`);
        if (!deleteDisk) return;
        
        const deleteFromDisk = confirm("تحذير: هل تريد حذف مجلد المستودع نهائياً من جهاز الكمبيوتر أيضاً؟ (لا يمكن التراجع عن هذا الإجراء)");
        
        deleteRepository(activeRepoIndex, deleteFromDisk);
    };
    
    // Cloud Config Add Device Row
    document.getElementById("add-device-row").onclick = () => {
        activeDevices.push({
            name: "",
            id: "",
            features: allFeatures.map(f => f[0]) // Select all by default
        });
        renderDevices();
    };
    
    // Save/Upload Cloud Config
    document.getElementById("save-config-local").onclick = () => saveCloudConfig(false);
    document.getElementById("save-config-cloud").onclick = () => saveCloudConfig(true);

    // Save/Upload Announcements (uses the same endpoint as Cloud Config)
    document.getElementById("add-announcement-row").onclick = () => {
        announcementsList.push({
            id: "ann_" + Date.now(),
            title: "",
            message: "",
            targetMode: "all",
            targetDevices: []
        });
        renderAnnouncements();
    };
    document.getElementById("save-announcements-local").onclick = () => saveCloudConfig(false);
    document.getElementById("save-announcements-cloud").onclick = () => saveCloudConfig(true);
    
    // Features Selection modal save
    document.getElementById("btn-features-modal-save").onclick = saveFeaturesModalSelection;
    
    // Save/Upload App Update Info
    document.getElementById("save-update-local").onclick = () => saveAppUpdate(false);
    document.getElementById("save-update-cloud").onclick = () => saveAppUpdate(true);
    
    // Create Git Tag
    document.getElementById("btn-create-tag").onclick = createGitTag;
    
    // Pull / Sync
    document.getElementById("btn-git-pull").onclick = () => performGitPull(false);
    document.getElementById("btn-refresh-lists").onclick = refreshLists;
    
    // Split List Actions
    document.getElementById("btn-edit-selected").onclick = () => {
        if (selectedLocalIndex === -1) {
            alert("الرجاء اختيار ملف من القائمة المحلية أولاً لتعديله!");
            return;
        }
        const file = localItems[selectedLocalIndex];
        if (file.type === "folder") {
            alert("لا يمكن تعديل المجلدات كملفات نصية!");
            return;
        }
        editFile(file.name);
    };
    
    document.getElementById("btn-delete-local").onclick = () => {
        if (selectedLocalIndex === -1) {
            alert("الرجاء اختيار عنصر لحذفه محلياً!");
            return;
        }
        const item = localItems[selectedLocalIndex];
        const label = (currentTab === "tags-manager") ? "التاج" : (item.type === "folder" ? "المجلد" : "الملف");
        if (confirm(`هل أنت متأكد من حذف ${label} (${item.name || item}) نهائياً من جهازك؟`)) {
            deleteLocalItem(item.name || item);
        }
    };
    
    document.getElementById("btn-delete-remote").onclick = () => {
        if (selectedRemoteIndex === -1) {
            alert("الرجاء اختيار عنصر لحذفه من GitHub!");
            return;
        }
        const item = remoteItems[selectedRemoteIndex];
        const label = (currentTab === "tags-manager") ? "التاج" : (item.type === "folder" ? "المجلد" : "الملف");
        if (confirm(`تحذير هام جداً: هل أنت متأكد تماماً من رغبتك في حذف ${label} (${item.name || item}) نهائياً من مستودع GitHub على السحاب؟`)) {
            deleteRemoteItem(item.name || item);
        }
    };
    
    // Push Source Code Modifications
    document.getElementById("btn-push-source").onclick = pushSourceCode;
    
    // Analytics Refresh Button
    const refreshBtn = document.getElementById("btn-analytics-refresh");
    if (refreshBtn) {
        refreshBtn.onclick = refreshAnalyticsFromCloud;
    }
}

// Add/Edit/Delete repo API calls
async function submitAddRepo() {
    const name = document.getElementById("modal-repo-name").value.trim();
    const url = document.getElementById("modal-repo-url").value.trim();
    if (!name || !url) {
        playSound("error");
        return alert("الرجاء إدخال اسم المستودع ورابطه!");
    }
    
    document.getElementById("btn-repo-modal-submit").disabled = true;
    try {
        const res = await fetch(`${API_BASE}/repos`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name, url })
        });
        const data = await res.json();
        if (data.success) {
            playSound("success");
            closeModal("repo-modal");
            alert("تم استنساخ المستودع بنجاح وإضافته لقائمة العمل!");
            fetchRepos();
        } else {
            playSound("error");
            alert("فشل استنساخ المستودع:\n" + data.error);
        }
    } catch(e) {
        playSound("error");
        alert("حدث خطأ أثناء الاتصال بالخادم: " + e);
    }
    document.getElementById("btn-repo-modal-submit").disabled = false;
}

async function submitEditRepo() {
    const name = document.getElementById("modal-repo-name").value.trim();
    const path = document.getElementById("modal-repo-path").value.trim();
    const url = document.getElementById("modal-repo-url").value.trim();
    if (!name || !path || !url) {
        playSound("error");
        return alert("الرجاء إدخال كافة البيانات ورابط المستودع!");
    }
    
    try {
        const res = await fetch(`${API_BASE}/repos/edit`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ index: activeRepoIndex, name, path, url })
        });
        const data = await res.json();
        if (data.success) {
            playSound("success");
            closeModal("repo-modal");
            alert("تم حفظ التعديلات بنجاح!");
            fetchRepos();
        } else {
            playSound("error");
            alert("خطأ: " + data.error);
        }
    } catch(e) {
        playSound("error");
        alert("خطأ: " + e);
    }
}

async function deleteRepository(idx, deleteDisk) {
    try {
        const res = await fetch(`${API_BASE}/repos/delete`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ index: idx, delete_disk: deleteDisk })
        });
        const data = await res.json();
        if (data.success) {
            playSound("success");
            alert("تم إزالة المستودع بنجاح.");
            fetchRepos();
        } else {
            playSound("error");
            alert("فشل حذف المستودع: " + data.error);
        }
    } catch (e) {
        playSound("error");
        alert("فشل إزالة المستودع: " + e);
    }
}

// Save Cloud Config JSON
async function saveCloudConfig(upload = false) {
    try {
        const token = document.getElementById("github-token")?.value || "";
        const repo = document.getElementById("github-repo")?.value || "";
        const res = await fetch(`${API_BASE}/cloud_config`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ 
                devices: activeDevices, 
                announcements: announcementsList, 
                github_token: token,
                github_repo: repo,
                upload 
            })
        });
        const data = await res.json();
        if (data.success) {
            alert(upload ? "تم الرفع والتطبيق على السحابة بنجاح!" : "تم حفظ الإعدادات السحابية محلياً بنجاح!");
            refreshLists();
        } else {
            alert("خطأ: " + data.error);
        }
    } catch(e) {
        alert("خطأ: " + e);
    }
}

// Save App Update Info JSON
async function saveAppUpdate(upload = false) {
    const vc = parseInt(document.getElementById("update-vc").value);
    const vn = document.getElementById("update-vn").value.trim();
    const url = document.getElementById("update-url").value.trim();
    const notes = document.getElementById("update-notes").value;
    
    if (isNaN(vc) || !vn || !url) {
        alert("يرجى ملء جميع الحقول المطلوبة بشكل صحيح!");
        return;
    }
    
    try {
        const res = await fetch(`${API_BASE}/app_update`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ versionCode: vc, versionName: vn, downloadUrl: url, releaseNotes: notes, upload })
        });
        const data = await res.json();
        if (data.success) {
            alert(upload ? "تم الرفع والتطبيق على السحابة بنجاح!" : "تم حفظ تحديث التطبيق محلياً بنجاح!");
            refreshLists();
        } else {
            alert("خطأ: " + data.error);
        }
    } catch(e) {
        alert("خطأ: " + e);
    }
}

// Create Git Tag
async function createGitTag() {
    const tag = document.getElementById("tag-name").value.trim();
    const msg = document.getElementById("tag-message").value.trim();
    if (!tag) return alert("يرجى إدخال اسم الوسم الجديد!");
    
    try {
        const res = await fetch(`${API_BASE}/tags`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ tag, message: msg })
        });
        const data = await res.json();
        if (res.status === 200) {
            alert("تم إنشاء ورفع التاج (Tag) بنجاح إلى GitHub!");
            document.getElementById("tag-name").value = "";
            document.getElementById("tag-message").value = "";
            refreshLists();
        } else {
            alert("فشل إنشاء التاج:\n" + data.error);
        }
    } catch(e) {
        alert("خطأ: " + e);
    }
}

// Delete Local Item (Tag or File)
async function deleteLocalItem(name) {
    const isTagMode = (currentTab === "tags-manager");
    const endpoint = isTagMode ? "tags/delete" : "delete_file";
    const body = isTagMode ? { tag: name, location: "local" } : { file: name, location: "local" };
    
    try {
        const res = await fetch(`${API_BASE}/${endpoint}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });
        const data = await res.json();
        if (data.success) {
            alert("تم الحذف محلياً بنجاح!");
            refreshLists();
        } else {
            alert("فشل الحذف: " + data.error);
        }
    } catch(e) {
        alert("خطأ: " + e);
    }
}

// Delete Remote Item (Tag or File)
async function deleteRemoteItem(name) {
    const isTagMode = (currentTab === "tags-manager");
    const endpoint = isTagMode ? "tags/delete" : "delete_file";
    const body = isTagMode ? { tag: name, location: "remote" } : { file: name, location: "remote" };
    
    try {
        const res = await fetch(`${API_BASE}/${endpoint}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });
        const data = await res.json();
        if (data.success) {
            alert("تم إزالة العنصر من السيرفر السحابي بنجاح!");
            refreshLists();
        } else {
            alert("فشل الحذف من السحاب:\n" + data.error);
        }
    } catch(e) {
        alert("خطأ: " + e);
    }
}

// Perform Git Pull / Fetch Tags
async function performGitPull() {
    const isTagMode = (currentTab === "tags-manager");
    try {
        const res = await fetch(`${API_BASE}/git_pull`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ mode: isTagMode ? "tags" : "files" })
        });
        const data = await res.json();
        if (data.success) {
            alert(isTagMode ? "تم جلب وتحديث جميع وسوم الإصدارات (Tags) بنجاح!" : "تمت مزامنة وجلب أحدث الملفات بنجاح من GitHub!");
            refreshLists();
        } else {
            alert("فشل المزامنة مع GitHub:\n" + data.error);
        }
    } catch(e) {
        alert("خطأ: " + e);
    }
}

// Edit File Modal
let currentEditingFilename = "";
async function editFile(filename) {
    const allowed = [".json", ".xml", ".txt", ".md", ".kt", ".java", ".gradle", ".properties", ".py", ".bat", ".sh", ".gitignore"];
    const ext = filename.substring(filename.lastIndexOf(".")).toLowerCase();
    if (!allowed.includes(ext)) {
        alert("هذا الملف قد يكون ملفاً ثنائياً ولا يمكن تعديله كصيغة نصية!");
        return;
    }
    
    try {
        const res = await fetch(`${API_BASE}/file_content?file=${filename}`);
        const data = await res.json();
        if (data.content !== undefined) {
            currentEditingFilename = filename;
            document.getElementById("editor-modal-filename").textContent = filename;
            document.getElementById("editor-textarea").value = data.content;
            
            document.getElementById("btn-editor-save").onclick = saveEditingFile;
            openModal("editor-modal");
        } else {
            alert("فشل قراءة الملف: " + data.error);
        }
    } catch (e) {
        alert("خطأ: " + e);
    }
}

async function saveEditingFile() {
    const content = document.getElementById("editor-textarea").value;
    try {
        const res = await fetch(`${API_BASE}/save_file`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ file: currentEditingFilename, content })
        });
        const data = await res.json();
        if (data.success) {
            alert("تم حفظ الملف بنجاح!");
            closeModal("editor-modal");
            refreshLists();
        } else {
            alert("فشل الحفظ: " + data.error);
        }
    } catch(e) {
        alert("خطأ: " + e);
    }
}

// Push Source Code changes
async function pushSourceCode() {
    const message = document.getElementById("commit-message").value.trim();
    if (!message) return alert("يجب كتابة رسالة التحديث (Commit Message) تصف التعديلات قبل الرفع!");
    
    const consoleBox = document.getElementById("git-log-output");
    consoleBox.textContent = "جاري رصد التغييرات والرفع إلى السيرفر...";
    
    try {
        const res = await fetch(`${API_BASE}/push_source`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ message })
        });
        const data = await res.json();
        if (res.status === 200) {
            consoleBox.textContent = data.logs || "تمت العملية بنجاح.";
            alert("تم رفع وحفظ التعديلات بنجاح إلى GitHub!");
            document.getElementById("commit-message").value = "";
            refreshLists();
        } else {
            consoleBox.textContent = data.error || "فشل الرفع.";
            alert("حدث خطأ أثناء الرفع، راجع السجل لمعرفة التفاصيل.");
        }
    } catch (e) {
        consoleBox.textContent = "Error: " + e;
        alert("خطأ: " + e);
    }
}

// Modal helper controls
function openModal(id) {
    document.getElementById(id).classList.add("active");
}

window.closeModal = function(id) {
    document.getElementById(id).classList.remove("active");
    if (id === "features-modal") activeFeaturesDeviceIndex = -1;
    if (id === "editor-modal") currentEditingFilename = "";
};

function playSound(type) {
    try {
        const ctx = new (window.AudioContext || window.webkitAudioContext)();
        if (type === "success") {
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.frequency.setValueAtTime(523.25, ctx.currentTime); // C5
            osc.frequency.setValueAtTime(659.25, ctx.currentTime + 0.12); // E5
            gain.gain.setValueAtTime(0.08, ctx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.4);
            osc.start();
            osc.stop(ctx.currentTime + 0.4);
        } else if (type === "error") {
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.frequency.setValueAtTime(220, ctx.currentTime);
            osc.frequency.linearRampToValueAtTime(110, ctx.currentTime + 0.25);
            gain.gain.setValueAtTime(0.12, ctx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.25);
            osc.start();
            osc.stop(ctx.currentTime + 0.25);
        }
    } catch(e) {
        console.error("Audio Context not supported or allowed", e);
    }
}

function renderAnnouncements() {
    const container = document.getElementById("announcements-list");
    if (!container) return;
    container.innerHTML = "";
    
    announcementsList.forEach((ann, idx) => {
        const item = document.createElement("div");
        item.className = "announcement-item";
        item.style.cssText = "display: flex; gap: 15px; background: rgba(255,255,255,0.06); padding: 15px; border-radius: 10px; align-items: center; margin-bottom: 15px;";
        
        if (!ann.targetDevices) {
            ann.targetDevices = [];
        }
        const targetMode = (ann.targetDevices && ann.targetDevices.length > 0) ? "selected" : "all";
        
        let devicesCheckboxesHtml = "";
        activeDevices.forEach((dev) => {
            const isChecked = ann.targetDevices.includes(dev.id) ? "checked" : "";
            const displayName = dev.name ? `${dev.name} (${dev.id.substring(0, 8)})` : dev.id.substring(0, 12);
            devicesCheckboxesHtml += `
                <label style="display: inline-flex; align-items: center; gap: 5px; margin-right: 15px; font-size: 13px; cursor: pointer; color: var(--text-primary); direction: rtl;">
                    <input type="checkbox" value="${dev.id}" ${isChecked} onchange="toggleDeviceTarget(${idx}, '${dev.id}', this.checked)">
                    ${displayName}
                </label>
            `;
        });
        
        item.innerHTML = `
            <div style="display: flex; flex-direction: column; gap: 8px; flex: 1; direction: rtl;">
                <div style="display: flex; gap: 10px;">
                    <input type="text" style="flex: 1; font-weight: bold;" value="${ann.title || ''}" placeholder="عنوان الإعلان (مثال: تحديث هام)" onchange="updateAnnouncementField(${idx}, 'title', this.value)">
                    <input type="hidden" value="${ann.id || 'ann_' + Date.now()}" onchange="updateAnnouncementField(${idx}, 'id', this.value)">
                </div>
                <textarea rows="2" style="width: 100%; resize: vertical;" placeholder="نص الإعلان أو الرسالة العامة..." onchange="updateAnnouncementField(${idx}, 'message', this.value)">${ann.message || ''}</textarea>
                
                <!-- Target Devices UI -->
                <div style="margin-top: 5px; display: flex; flex-direction: column; gap: 5px;">
                    <div style="display: flex; align-items: center; gap: 15px; font-size: 13px; color: var(--text-secondary);">
                        <span>استهداف الأجهزة:</span>
                        <label style="display: inline-flex; align-items: center; gap: 5px; cursor: pointer;">
                            <input type="radio" name="targetMode_${idx}" value="all" ${targetMode === "all" ? "checked" : ""} onchange="setTargetMode(${idx}, 'all')">
                            جميع الأجهزة
                        </label>
                        <label style="display: inline-flex; align-items: center; gap: 5px; cursor: pointer;">
                            <input type="radio" name="targetMode_${idx}" value="selected" ${targetMode === "selected" ? "checked" : ""} onchange="setTargetMode(${idx}, 'selected')">
                            أجهزة محددة
                        </label>
                    </div>
                    
                    <div id="target-devices-panel-${idx}" style="display: ${targetMode === "selected" ? "flex" : "none"}; flex-wrap: wrap; gap: 8px; background: rgba(0,0,0,0.15); padding: 10px; border-radius: 6px; margin-top: 5px;">
                        ${devicesCheckboxesHtml || "<span style='font-size: 12px; color: var(--text-secondary);'>لا توجد أجهزة مسجلة حالياً.</span>"}
                    </div>
                </div>
            </div>
            <button class="btn btn-danger btn-small" onclick="removeAnnouncementRow(${idx})">إزالة</button>
        `;
        container.appendChild(item);
    });
    
    if (announcementsList.length === 0) {
        container.innerHTML = "<p style='color: var(--text-secondary); text-align: center; padding: 20px;'>لا توجد إعلانات عامة حالياً. اضغط إضافة إعلان جديد.</p>";
    }
}

window.updateAnnouncementField = function(idx, field, value) {
    if (announcementsList[idx]) {
        announcementsList[idx][field] = value.trim();
    }
};

window.setTargetMode = function(idx, mode) {
    if (announcementsList[idx]) {
        if (mode === "all") {
            announcementsList[idx].targetDevices = [];
        } else {
            if (!announcementsList[idx].targetDevices) {
                announcementsList[idx].targetDevices = [];
            }
        }
        renderAnnouncements();
    }
};

window.toggleDeviceTarget = function(idx, deviceId, checked) {
    if (announcementsList[idx]) {
        if (!announcementsList[idx].targetDevices) {
            announcementsList[idx].targetDevices = [];
        }
        const index = announcementsList[idx].targetDevices.indexOf(deviceId);
        if (checked) {
            if (index === -1) {
                announcementsList[idx].targetDevices.push(deviceId);
            }
        } else {
            if (index !== -1) {
                announcementsList[idx].targetDevices.splice(index, 1);
            }
        }
    }
};

window.removeAnnouncementRow = function(idx) {
    announcementsList.splice(idx, 1);
    renderAnnouncements();
};

async function fetchAnalytics() {
    try {
        // Fetch cloud config to ensure we have the whitelisted devices names/features
        const configRes = await fetch(`${API_BASE}/cloud_config`);
        const configData = await configRes.json();
        activeDevices = configData.devices || [];
        
        // Fetch telemetry stats
        const res = await fetch(`${API_BASE}/analytics`);
        const data = await res.json();
        analyticsStats = data.stats || [];
        
        // Fetch crashes count
        const crashRes = await fetch(`${API_BASE}/crashes`);
        const crashData = await crashRes.json();
        crashesList = crashData.crashes || [];
        
        // 1. Calculate Stats
        document.getElementById("stats-total-devices").textContent = activeDevices.length;
        document.getElementById("stats-active-crashes").textContent = crashesList.length;
        
        // Calculate Top Feature
        const featureUsage = {};
        analyticsStats.forEach(stat => {
            const usage = stat.featuresUsage || {};
            Object.keys(usage).forEach(fKey => {
                featureUsage[fKey] = (featureUsage[fKey] || 0) + (usage[fKey] || 0);
            });
        });
        
        let topFeatureKey = "";
        let maxClicks = 0;
        Object.keys(featureUsage).forEach(fKey => {
            if (featureUsage[fKey] > maxClicks) {
                maxClicks = featureUsage[fKey];
                topFeatureKey = fKey;
            }
        });
        
        if (topFeatureKey) {
            const featLabel = allFeatures.find(f => f[0] === topFeatureKey)?.[1] || topFeatureKey;
            document.getElementById("stats-top-feature").innerHTML = `${featLabel} <span style="font-size: 11px; color: var(--text-secondary);">(${maxClicks} نقرة)</span>`;
        } else {
            document.getElementById("stats-top-feature").textContent = "لا يوجد بيانات";
        }
        
        renderTopToolsChart(featureUsage);
        renderAnalytics();
    } catch (e) {
        console.error("Failed fetching analytics data", e);
    }
}

function renderAnalytics(filterText = "") {
    const container = document.getElementById("analytics-device-list");
    if (!container) return;
    container.innerHTML = "";
    
    const query = filterText.toLowerCase().trim();
    
    // Mix whitelisted devices with stats
    const displayDevices = [];
    activeDevices.forEach(dev => {
        const stats = analyticsStats.find(s => s.deviceId === dev.id);
        const devObj = {
            id: dev.id,
            name: dev.name,
            whitelisted: true,
            features: dev.features,
            stats: stats
        };
        
        if (!query || dev.name.toLowerCase().includes(query) || dev.id.toLowerCase().includes(query)) {
            displayDevices.push(devObj);
        }
    });
    
    // Also add stats-only devices (devices that sent statistics but not currently in whitelist)
    analyticsStats.forEach(stat => {
        if (!activeDevices.some(d => d.id === stat.deviceId)) {
            const devObj = {
                id: stat.deviceId,
                name: stat.deviceName || "جهاز غير مسجل",
                whitelisted: false,
                features: [],
                stats: stat
            };
            if (!query || devObj.name.toLowerCase().includes(query) || stat.deviceId.toLowerCase().includes(query)) {
                displayDevices.push(devObj);
            }
        }
    });
    
    displayDevices.forEach(item => {
        const row = document.createElement("div");
        row.className = "analytics-device-item";
        row.style.cssText = "display: flex; justify-content: space-between; align-items: center; padding: 15px 20px; border-bottom: 1px solid rgba(255,255,255,0.06); gap: 20px; flex-wrap: wrap; background: rgba(255,255,255,0.01);";
        
        const lastActiveDate = item.stats?.lastActive ? new Date(item.stats.lastActive).toLocaleString("ar-EG") : "غير معروف";
        
        // Active Status Indicator Badge calculation
        const now = Date.now();
        const diffMs = item.stats?.lastActive ? (now - item.stats.lastActive) : null;
        let statusClass = "status-inactive";
        let statusName = "غير متصل";
        
        if (diffMs !== null) {
            const diffHours = diffMs / (1000 * 60 * 60);
            if (diffHours <= 24) {
                statusClass = "status-active";
                statusName = "🟢 نشط حالياً";
            } else if (diffHours <= 168) {
                statusClass = "status-idle";
                statusName = "🟡 خامل (آخر أسبوع)";
            } else {
                statusClass = "status-inactive";
                statusName = "🔴 غير متصل (منذ أكثر من أسبوع)";
            }
        } else {
            statusName = "🔴 غير متصل/خامل";
        }

        // Build feature usage string
        let usageSummary = "";
        if (item.stats?.featuresUsage) {
            const usages = [];
            Object.keys(item.stats.featuresUsage).forEach(fKey => {
                const label = allFeatures.find(f => f[0] === fKey)?.[1] || fKey;
                usages.push(`${label} (${item.stats.featuresUsage[fKey]})`);
            });
            usageSummary = usages.length > 0 ? usages.join(" ، ") : "لم يستعمل أي خدمة بعد";
        } else {
            usageSummary = "لا يوجد بيانات استخدام بعد";
        }
        
        // Side-by-Side Naming Formatting
        const assignedName = item.name || "";
        const realName = item.stats?.deviceName || "";
        let displayName = "";
        if (item.whitelisted) {
            displayName = realName ? `${assignedName} (${realName})` : assignedName;
        } else {
            displayName = realName ? `${realName} (جهاز غير مسجل)` : "جهاز غير مسجل";
        }
        
        row.innerHTML = `
            <div style="flex: 1; min-width: 250px; text-align: right;">
                <div style="display: flex; gap: 10px; align-items: center; margin-bottom: 5px; flex-wrap: wrap;">
                    <strong style="color: #fff; font-size: 16px;">${displayName}</strong>
                    ${item.whitelisted ? '<span style="font-size: 11px; background: rgba(16,185,129,0.2); color: #10B981; padding: 2px 8px; border-radius: 50px;">نشط في القائمة</span>' : '<span style="font-size: 11px; background: rgba(239,68,68,0.2); color: #EF4444; padding: 2px 8px; border-radius: 50px;">غير مصرح له</span>'}
                    <span class="status-badge ${statusClass}">${statusName}</span>
                </div>
                <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 6px;">معرف الجهاز: <code style="color: var(--accent); font-family: monospace;">${item.id}</code></div>
                <div style="font-size: 12px; color: var(--text-secondary);">آخر نشاط: <span style="color: #fff;">${lastActiveDate}</span></div>
                <div style="font-size: 13px; color: #10B981; margin-top: 8px; font-weight: 500;">الاستخدام: <span style="color: var(--text-secondary); font-weight: normal;">${usageSummary}</span></div>
            </div>
            <div>
                ${item.whitelisted ? `<button class="btn btn-secondary btn-small" onclick="editDevicePermissions('${item.id}', '${item.name}')">تعديل الصلاحيات ⚙️</button>` : `<button class="btn btn-accent btn-small" onclick="whitelistDevice('${item.id}', '${item.name}')">تفعيل وترخيص الجهاز 🔓</button>`}
            </div>
        `;
        container.appendChild(row);
    });
    
    if (displayDevices.length === 0) {
        container.innerHTML = "<p style='color: var(--text-secondary); text-align: center; padding: 30px;'>لا توجد أجهزة مطابقة للبحث.</p>";
    }
}

window.onAnalyticsSearch = function(val) {
    renderAnalytics(val);
};

window.editDevicePermissions = function(deviceId, deviceName) {
    const devIdx = activeDevices.findIndex(d => d.id === deviceId);
    if (devIdx !== -1) {
        // reuse the whitelists features modal in app.js
        showFeaturesModal(devIdx);
    }
};

window.whitelistDevice = function(deviceId, deviceName) {
    // Add to activeDevices list
    activeDevices.push({
        id: deviceId,
        name: deviceName,
        features: allFeatures.map(f => f[0]) // authorize all by default
    });
    renderAnalytics();
    // highlight on save config local
    alert(`تم إضافة الجهاز '${deviceName}' لقائمة المصرح لهم! تذكر حفظ التغييرات محلياً أو رفعها للسحاب لتطبيق التفعيل.`);
};

async function fetchCrashes() {
    try {
        const res = await fetch(`${API_BASE}/crashes`);
        const data = await res.json();
        crashesList = data.crashes || [];
        renderCrashes();
    } catch (e) {
        console.error("Failed fetching crashes", e);
    }
}

function renderCrashes() {
    const container = document.getElementById("crashes-list");
    if (!container) return;
    container.innerHTML = "";
    
    crashesList.forEach(crash => {
        const card = document.createElement("div");
        card.className = "crash-card";
        card.style.cssText = "background: rgba(239,68,68,0.03); border: 1px solid rgba(239,68,68,0.15); border-radius: 12px; padding: 20px; text-align: right; display: flex; flex-direction: column; gap: 10px;";
        
        const dateStr = new Date(crash.timestamp).toLocaleString("ar-EG");
        const detailsId = `trace-${crash.filename.replace(/\./g, "-")}`;
        
        card.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 15px;">
                <div>
                    <h3 style="color: #EF4444; margin-bottom: 5px; font-size: 16px;">🚨 انهيار في جهاز: ${crash.deviceName} (${crash.androidVersion})</h3>
                    <div style="font-size: 12px; color: var(--text-secondary);">تاريخ الانهيار: <span style="color: #fff;">${dateStr}</span> | معرف الجهاز: <code style="color: var(--accent);">${crash.deviceId}</code></div>
                </div>
                <button class="btn btn-small btn-primary" onclick="resolveCrash('${crash.filename}')">تم الحل وأرشفة السجل ✅</button>
            </div>
            
            <div style="background: rgba(0,0,0,0.3); padding: 10px; border-radius: 6px; border-right: 4px solid #EF4444; font-weight: bold; color: #fff; margin-top: 5px;">
                ${crash.errorMessage}
            </div>
            
            <div style="margin-top: 8px;">
                <button class="btn btn-secondary btn-small" onclick="toggleElement('${detailsId}')">عرض تفاصيل مسار الانهيار (Stack Trace) 🔍</button>
                <div id="${detailsId}" style="display: none; margin-top: 10px; background: #1E1E1E; padding: 15px; border-radius: 8px; border: 1px solid rgba(255,255,255,0.05); overflow-x: auto;">
                    <pre style="color: #FF6B6B; font-family: monospace; font-size: 12px; line-height: 1.5; white-space: pre-wrap; text-align: left; direction: ltr;">${crash.stackTrace}</pre>
                </div>
            </div>
        `;
        container.appendChild(card);
    });
    
    if (crashesList.length === 0) {
        container.innerHTML = "<p style='color: #10B981; text-align: center; padding: 30px; font-weight: 500;'>🎉 رائع! لا توجد سجلات أخطاء أو انهيارات نشطة حالياً.</p>";
    }
}

window.toggleElement = function(id) {
    const el = document.getElementById(id);
    if (el) {
        el.style.display = el.style.display === "none" ? "block" : "none";
    }
};

async function resolveCrash(filename) {
    if (!confirm("هل أنت متأكد من حل هذه المشكلة وحذف تقرير الانهيار نهائياً من مستودع السحابة؟")) return;
    try {
        const res = await fetch(`${API_BASE}/crashes/resolve`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ filename: filename, upload: true })
        });
        const data = await res.json();
        if (data.success) {
            alert("تم أرشفة وحذف سجل الانهيار بنجاح!");
            fetchAnalytics(); // reload stats card count
            fetchCrashes();
        } else {
            alert("فشل الأرشفة: " + data.error);
        }
    } catch (e) {
        alert("خطأ: " + e);
    }
}

async function refreshAnalyticsFromCloud() {
    const btn = document.getElementById("btn-analytics-refresh");
    if (!btn) return;
    btn.disabled = true;
    btn.textContent = "جاري التحديث... ⏳";
    
    try {
        const res = await fetch(`${API_BASE}/git_pull`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ mode: "files" })
        });
        const data = await res.json();
        if (data.success) {
            alert("تمت مزامنة وجلب أحدث البيانات من GitHub بنجاح!");
            await fetchAnalytics();
        } else {
            alert("فشل المزامنة مع GitHub:\n" + data.error);
        }
    } catch (e) {
        alert("حدث خطأ أثناء التحديث: " + e);
    } finally {
        btn.disabled = false;
        btn.textContent = "تحديث من السحابة 🔄";
    }
}

let autoSyncInterval = null;

window.toggleAnalyticsAutoSync = function(enabled) {
    if (enabled) {
        autoSyncInterval = setInterval(async () => {
            const searchVal = document.getElementById("analytics-search")?.value || "";
            if (!searchVal.trim()) {
                await doSilentGitPull();
            }
        }, 60000); // 1 minute
    } else {
        if (autoSyncInterval) {
            clearInterval(autoSyncInterval);
            autoSyncInterval = null;
        }
    }
};

async function doSilentGitPull() {
    try {
        const res = await fetch(`${API_BASE}/git_pull`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ mode: "silent" })
        });
        if (res.ok) {
            await fetchAnalytics();
        }
    } catch (e) {
        console.error("Silent git pull failed", e);
    }
}

window.exportAnalyticsCSV = function() {
    const exportDevices = [];
    activeDevices.forEach(dev => {
        const stats = analyticsStats.find(s => s.deviceId === dev.id);
        exportDevices.push({
            id: dev.id,
            name: dev.name,
            whitelisted: true,
            features: dev.features,
            stats: stats
        });
    });
    analyticsStats.forEach(stat => {
        if (!activeDevices.some(d => d.id === stat.deviceId)) {
            exportDevices.push({
                id: stat.deviceId,
                name: stat.deviceName || "جهاز غير مسجل",
                whitelisted: false,
                features: [],
                stats: stat
            });
        }
    });

    if (exportDevices.length === 0) {
        alert("لا توجد بيانات لتصديرها!");
        return;
    }

    let csvContent = "\uFEFF"; // Byte Order Mark for Excel Arabic support
    csvContent += "معرّف الجهاز (ID),الاسم المسجل,الاسم الفعلي (الموديل),حالة الترخيص,آخر نشاط,مؤشر النشاط,الاستخدام بالتفصيل\n";
    
    exportDevices.forEach(item => {
        const lastActiveDate = item.stats?.lastActive ? new Date(item.stats.lastActive).toLocaleString("ar-EG").replace(/,/g, "") : "غير معروف";
        const licenseStatus = item.whitelisted ? "مفعّل" : "غير مفعّل";
        
        let statusName = "غير متصل";
        if (item.stats?.lastActive) {
            const diffHours = (Date.now() - item.stats.lastActive) / (1000 * 60 * 60);
            if (diffHours <= 24) {
                statusName = "نشط (خلال 24 ساعة)";
            } else if (diffHours <= 168) {
                statusName = "خامل (آخر أسبوع)";
            } else {
                statusName = "غير نشط (أكثر من أسبوع)";
            }
        }

        let usages = [];
        if (item.stats?.featuresUsage) {
            Object.keys(item.stats.featuresUsage).forEach(fKey => {
                const label = allFeatures.find(f => f[0] === fKey)?.[1] || fKey;
                usages.push(`${label}: ${item.stats.featuresUsage[fKey]}`);
            });
        }
        const usageSummary = usages.length > 0 ? usages.join(" | ") : "لم يستعمل أي خدمة";

        const row = [
            item.id,
            (item.name || "").replace(/"/g, '""'),
            (item.stats?.deviceName || "").replace(/"/g, '""'),
            licenseStatus,
            lastActiveDate,
            statusName,
            `"${usageSummary.replace(/"/g, '""')}"`
        ];
        csvContent += row.join(",") + "\n";
    });

    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", `analytics_report_${new Date().toISOString().slice(0, 10)}.csv`);
    link.style.visibility = "hidden";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
};

let topToolsChartInstance = null;

function renderTopToolsChart(featureUsage) {
    const canvas = document.getElementById("topToolsChart");
    if (!canvas) return;

    const sortedFeatures = Object.keys(featureUsage)
        .map(fKey => {
            const label = allFeatures.find(f => f[0] === fKey)?.[1] || fKey;
            return {
                key: fKey,
                label: label,
                count: featureUsage[fKey]
            };
        })
        .sort((a, b) => b.count - a.count)
        .slice(0, 5);

    const labels = sortedFeatures.map(item => item.label);
    const dataCounts = sortedFeatures.map(item => item.count);

    if (topToolsChartInstance) {
        topToolsChartInstance.destroy();
    }

    const ctx = canvas.getContext('2d');
    topToolsChartInstance = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: 'عدد مرات الاستخدام',
                data: dataCounts,
                backgroundColor: [
                    'rgba(59, 130, 246, 0.65)',
                    'rgba(16, 185, 129, 0.65)',
                    'rgba(139, 92, 246, 0.65)',
                    'rgba(245, 158, 11, 0.65)',
                    'rgba(239, 68, 68, 0.65)'
                ],
                borderColor: [
                    '#3b82f6',
                    '#10b981',
                    '#8b5cf6',
                    '#f59e0b',
                    '#ef4444'
                ],
                borderWidth: 1.5,
                borderRadius: 6
            }]
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    rtl: true,
                    titleFont: { family: 'Tajawal' },
                    bodyFont: { family: 'Tajawal' }
                }
            },
            scales: {
                x: {
                    grid: {
                        color: 'rgba(255, 255, 255, 0.05)'
                    },
                    ticks: {
                        color: '#94a3b8',
                        font: { family: 'Tajawal', size: 11 },
                        stepSize: 1
                    }
                },
                y: {
                    grid: {
                        display: false
                    },
                    ticks: {
                        color: '#e2e8f0',
                        font: { family: 'Tajawal', size: 12, weight: '600' }
                    }
                }
            }
        }
    });
}
