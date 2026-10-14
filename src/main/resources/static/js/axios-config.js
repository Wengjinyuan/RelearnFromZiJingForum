/* Translate the copied Vue pages' API contract at the browser boundary. */
(function () {
    const client = axios.create({ timeout: 15000 });
    const toggles = new Map();
    function roleCode(role) {
        if (role === 'SUPER_ADMIN') return 3;
        if (role === 'ADMIN') return 2;
        return 0;
    }
    function item(value) {
        if (!value || typeof value !== 'object') return value;
        const out = { ...value };
        if ('authorId' in out) Object.assign(out, {
            userId: out.authorId, username: out.authorName || out.authorAccount || '用户',
            createTime: out.createdAt, image: out.coverUrl || '', pinned: !!out.top,
            isEssence: !!out.essence,
            author: { id: out.authorId, username: out.authorName || out.authorAccount || '用户', role: 0 }
        });
        if ('displayName' in out || 'avatarUrl' in out) Object.assign(out, {
            id: out.userId || out.id, username: out.displayName || out.account || '用户',
            avatar: out.avatarUrl || '',
            gender: ({ UNKNOWN: 0, MALE: 1, FEMALE: 2 })[out.gender] ?? Number(out.gender || 0)
        });
        if ('postId' in out && 'content' in out) Object.assign(out, {
            userId: out.authorId, username: out.authorName, createTime: out.createdAt,
            subComments: page({ items: out.replies || [], page: 1, size: 5, total: (out.replies || []).length })
        });
        if ('kind' in out && 'read' in out) Object.assign(out, {
            type: String(out.kind).toLowerCase(), isRead: !!out.read, createTime: out.createdAt
        });
        return out;
    }
    function page(value) {
        value = value || {};
        const records = (value.items || []).map(item);
        const current = value.page || 1, size = value.size || 10, total = value.total || 0;
        return { records, list: records, current, size, total,
            pages: Math.ceil(total / size), hasNext: current * size < total };
    }
    function route(config, url, method) {
        config.url = url;
        if (method) config.method = method;
    }
    client.interceptors.request.use(config => {
        const old = config.url || '';
        config._oldUrl = old;
        let m;
        if (old === '/api/user/login') route(config, '/api/accounts/login');
        else if (old === '/api/user/register') route(config, '/api/accounts/register');
        else if (old === '/api/user/current') route(config, '/api/accounts/me');
        else if ((m = old.match(/^\/api\/user\/info\/(\d+)\/avatar$/))) route(config, '/api/profiles/me/avatar');
        else if ((m = old.match(/^\/api\/user\/info\/(\d+)$/))) {
            route(config, config.method === 'put' ? '/api/profiles/me' : `/api/profiles/${m[1]}`);
            if (config.method === 'put') {
                const form = typeof config.data === 'string' ? JSON.parse(config.data) : config.data;
                config.data = { displayName: form.username,
                    gender: ({ 0: 'UNKNOWN', 1: 'MALE', 2: 'FEMALE' })[form.gender] || 'UNKNOWN',
                    signature: form.signature, major: form.major, birthday: form.birthday || null };
            }
        }
        else if ((m = old.match(/^\/api\/user\/(\d+)\/(following|followers)$/))) route(config, `/api/follows/users/${m[1]}/${m[2]}`);
        else if ((m = old.match(/^\/api\/user\/follow\/status\/(\d+)$/))) route(config, `/api/follows/${m[1]}/status`);
        else if ((m = old.match(/^\/api\/user\/follow\/(\d+)$/))) route(config, `/api/follows/${m[1]}`, config.method === 'post' ? 'put' : config.method);
        else if (old === '/api/user/follow/feed') route(config, '/api/follows/feed');
        else if ((m = old.match(/^\/api\/user\/(\d+)\/posts$/))) {
            route(config, '/api/posts');
            config.params = { ...(config.params || {}), authorId: m[1] };
            if (Number(config.params.isEssence) === 1) config.params.essence = true;
        }
        else if (old === '/api/user/search') route(config, '/api/search/users');
        else if (old === '/api/home/getHotTags') route(config, '/api/home/hot-tags');
        else if (old === '/api/post/search') route(config, '/api/search/posts');
        else if (old === '/api/post/top') route(config, '/api/posts/top');
        else if (old === '/api/post') route(config, '/api/posts');
        else if ((m = old.match(/^\/api\/post\/(\d+)\/comments$/))) route(config,
            config.method === 'delete' ? `/api/comments/${m[1]}` : `/api/posts/${m[1]}/comments`);
        else if ((m = old.match(/^\/api\/post\/(\d+)\/subComments$/))) route(config, `/api/comments/${m[1]}/replies`);
        else if ((m = old.match(/^\/api\/post\/comment\/like\/(\d+)$/))) route(config, `/api/interactions/comments/${m[1]}/like`, 'put');
        else if ((m = old.match(/^\/api\/post\/(like|favorite)\/(\d+)$/))) {
            const key = `${localStorage.getItem('userId')}:${m[1]}:${m[2]}`;
            config._toggleKey = key;
            route(config, `/api/interactions/posts/${m[2]}/${m[1]}`);
            return client.get(`/api/interactions/posts/${m[2]}/state`).then(state => {
                const active = !!state.data.data?.[m[1] === 'like' ? 'liked' : 'favorited'];
                config._toggleNext = !active;
                config.method = active ? 'delete' : 'put';
                const token = localStorage.getItem('token');
                if (token) config.headers.Authorization = `Bearer ${token}`;
                return config;
            });
        }
        else if ((m = old.match(/^\/api\/post\/(\d+)$/))) route(config, `/api/posts/${m[1]}`);
        else if (old === '/api/notification/unreadCount') route(config, '/api/notifications/unread-count');
        else if (old === '/api/notification/list') route(config, '/api/notifications');
        else if (old === '/api/notification/readAll') route(config, '/api/notifications/read-all');
        else if ((m = old.match(/^\/api\/notification\/read\/(\d+)$/))) route(config, `/api/notifications/${m[1]}/read`);
        else if (old === '/api/admin/user/list') {
            route(config, '/api/admin/users');
            config.params = { page: 1, size: 100 };
        }
        else if (old === '/api/post/management/list') {
            route(config, '/api/admin/posts');
            config.params = {
                page: config.params?.pageNum || 1,
                size: config.params?.pageSize || 20,
                keyword: config.params?.keyword || undefined
            };
        }
        else if ((m = old.match(/^\/api\/post\/management\/(\d+)(?:\/(top|essence))?$/))) {
            route(config, `/api/admin/posts/${m[1]}/${m[2] || 'delete'}`, 'put');
            config.data = { reason: '管理后台操作', enabled: config.params?.enabled };
        }
        else if ((m = old.match(/^\/api\/admin\/user\/(ban|mute|unban|unmute)$/))) {
            const action = m[1];
            const days = Number(config.params?.timeType);
            route(config, `/api/admin/users/${config.params?.id}/${action}`, 'put');
            config.data = { reason: '管理后台操作', expiresAt: action === 'mute'
                ? new Date(Date.now() + (Number.isFinite(days) && days > 0 ? days : 3650) * 86400000).toISOString().slice(0, 19) : null };
        }
        if (config.params) {
            config.params = { ...config.params };
            if (config.params.current !== undefined) {
                config.params.page = old === '/api/post/search'
                    ? Math.max(0, Number(config.params.current) || 0) + 1
                    : Math.max(1, Number(config.params.current) || 1);
                delete config.params.current;
            }
            if (config.params.category === '精华') {
                config.params.essence = true;
                delete config.params.category;
            } else if (config.params.category === 'all') delete config.params.category;
        }
        const token = localStorage.getItem('token');
        if (token) config.headers.Authorization = `Bearer ${token}`;
        return config;
    });
    client.interceptors.response.use(response => {
        const old = response.config._oldUrl || '';
        const body = response.data || {};
        if (response.config._toggleKey) toggles.set(response.config._toggleKey, response.config._toggleNext);
        if (response.config.method === 'get' && (old === '/api/post'
            || /^\/api\/post\/\d+\/(comments|subComments)$/.test(old)
            || /^\/api\/user\/\d+\/(posts|following|followers)$/.test(old)
            || old === '/api/user/follow/feed' || old === '/api/post/search' || old === '/api/user/search'
            || old === '/api/notification/list')) {
            response.data = page(body.data);
            return addInteractionStates(response.data.records).then(() => response);
        }
        let data = body.data;
        let code = 1;
        if (old.startsWith('/api/admin/') || old.startsWith('/api/post/management/')) code = 200;
        if (old === '/api/admin/user/list') data = (data?.items || []).map(x => ({ ...x,
            username: x.account, status: x.status === 'BANNED' ? 0 : x.status === 'MUTED' ? 2 : 1 }));
        else if (old === '/api/post/management/list') data = { ...page(data), records: (data?.items || []).map(x => ({
            ...x, username: `用户${x.authorId}`, userId: x.authorId,
            isTop: x.top ? 1 : 0, isEssence: x.essence ? 1 : 0 })) };
        if (old === '/api/home' && data) data = { userCount: data.users, postCount: data.posts,
            todayCount: data.todayPosts, yesterdayCount: data.yesterdayPosts };
        else if (old === '/api/home/getHotTags') data = (data || []).map(x => ({ name: x.name, tagName: x.name,
            count: x.used, used: x.used, hotScore: x.used }));
        else if (old === '/api/user/login' && data) data = { ...data.user, token: data.accessToken,
            username: data.user.account, role: roleCode(data.user.role) };
        else if (old === '/api/user/current' && data) data = { ...data, username: data.account,
            role: roleCode(data.role) };
        else if (old === '/api/notification/unreadCount') data = data?.count || 0;
        else if (/^\/api\/user\/follow\/status\//.test(old)) data = !!data?.following;
        else if (Array.isArray(data)) data = data.map(item);
        else data = item(data);
        response.data = { code: response.config._toggleKey ? (response.config._toggleNext ? 1 : 0) : code,
            msg: body.message, data };
        if (/^\/api\/user\/info\/\d+$/.test(old) && response.config.method === 'get' && data) {
            return client.get(`/api/profiles/${data.id}/stats`).then(stats => {
                Object.assign(data, stats.data.data || {});
                return response;
            }).catch(() => response);
        }
        if (/^\/api\/post\/\d+$/.test(old) && response.config.method === 'get' && data) {
            return addInteractionStates([data]).then(() => response);
        }
        return response;
    }, error => {
        if (error.response?.status === 401 && localStorage.getItem('token')) {
            localStorage.removeItem('token');
            localStorage.removeItem('userInfo');
            if (!location.pathname.endsWith('/login.html')) location.href = '/login.html';
        }
        return Promise.reject(error);
    });
    function addInteractionStates(posts) {
        if (!localStorage.getItem('token') || !Array.isArray(posts)) return Promise.resolve();
        return Promise.all(posts.filter(p => p && p.id).map(post =>
            client.get(`/api/interactions/posts/${post.id}/state`).then(response => {
                const state = response.data.data || {};
                post.liked = !!state.liked;
                post.favorited = !!state.favorited;
                toggles.set(`${localStorage.getItem('userId')}:like:${post.id}`, post.liked);
                toggles.set(`${localStorage.getItem('userId')}:favorite:${post.id}`, post.favorited);
            }).catch(() => {})));
    }
    ['get', 'post', 'put', 'delete', 'patch', 'head', 'options', 'request'].forEach(method => {
        axios[method] = (...args) => client[method](...args);
    });
})();
