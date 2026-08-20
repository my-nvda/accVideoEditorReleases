/**
 * Cloudflare Worker: Secure GitHub Proxy for Telemetry and Crash Reports.
 * 
 * Securely route client-side telemetry and crash reports to GitHub API
 * without exposing the GitHub Personal Access Token (PAT) inside the client APK.
 * 
 * Environment Variables Required in Cloudflare Dashboard:
 * 1. GITHUB_TOKEN: Your GitHub Personal Access Token (with repo scope)
 * 2. GITHUB_REPO: Your GitHub repository (e.g., "username/repo_name")
 */

addEventListener('fetch', event => {
  event.respondWith(handleRequest(event.request))
})

async function handleRequest(request) {
  // Enforce CORS
  const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, X-Device-Id',
  }

  if (request.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders })
  }

  if (request.method !== 'POST') {
    return new Response(JSON.stringify({ error: 'Method not allowed' }), {
      status: 405,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    })
  }

  try {
    const envToken = typeof GITHUB_TOKEN !== 'undefined' ? GITHUB_TOKEN : null;
    const envRepo = typeof GITHUB_REPO !== 'undefined' ? GITHUB_REPO : null;

    if (!envToken || !envRepo) {
      return new Response(JSON.stringify({ error: 'Worker not properly configured. Missing GITHUB_TOKEN or GITHUB_REPO.' }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      })
    }

    const body = await request.json()
    const { path, content, message } = body

    if (!path || !content) {
      return new Response(JSON.stringify({ error: 'Missing path or content' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      })
    }

    // Ensure path goes only to allowed directories for security
    if (!path.startsWith('device_stats/') && !path.startsWith('crash_reports/')) {
      return new Response(JSON.stringify({ error: 'Unauthorized destination directory' }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      })
    }

    const apiUrl = `https://api.github.com/repos/${envRepo}/contents/${path}`

    // 1. Check if file exists to fetch existing SHA
    let sha = null
    const checkRes = await fetch(apiUrl, {
      method: 'GET',
      headers: {
        'Authorization': `token ${envToken}`,
        'User-Agent': 'Cloudflare-GitHub-Proxy',
        'Accept': 'application/vnd.github.v3+json'
      }
    })

    if (checkRes.status === 200) {
      const existingData = await checkRes.json()
      sha = existingData.sha
    }

    // 2. Put the new content
    const putBody = {
      message: message || `Telemetry upload: ${path}`,
      content: content,
      sha: sha || undefined
    }

    const writeRes = await fetch(apiUrl, {
      method: 'PUT',
      headers: {
        'Authorization': `token ${envToken}`,
        'Content-Type': 'application/json',
        'User-Agent': 'Cloudflare-GitHub-Proxy',
        'Accept': 'application/vnd.github.v3+json'
      },
      body: JSON.stringify(putBody)
    })

    const writeData = await writeRes.json()

    return new Response(JSON.stringify({
      success: writeRes.status === 200 || writeRes.status === 201,
      status: writeRes.status,
      githubResponse: writeData
    }), {
      status: writeRes.status,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    })

  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    })
  }
}
