package com.exteragram.messenger.adblock.interop;

import android.text.TextUtils;

import com.exteragram.messenger.adblock.data.BlockResult;
import com.exteragram.messenger.adblock.data.FilterListMetadata;
import com.exteragram.messenger.adblock.data.UrlCosmeticResources;

import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pure-Java EasyList/uBlock-compatible rule & cosmetic engine backing {@link AdBlock}.
 */
public class NativeAdBlock {

    private static final AtomicLong nextHandle = new AtomicLong(1);
    private static final ConcurrentHashMap<Long, FilterSet> filterSets = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Engine> engines = new ConcurrentHashMap<>();

    public static synchronized boolean loadLibraries() {
        return true;
    }

    public static boolean isLoadFailed() {
        return false;
    }

    private static final class Rule {
        final String raw;
        final boolean exception;
        final boolean domainAnchor;
        final String pattern;
        final boolean important;
        final int thirdParty; // 0 = any, 1 = third-party only, -1 = first-party only
        final Set<String> includeDomains;
        final Set<String> excludeDomains;
        final Set<String> resourceTypes;
        final String redirect;

        Rule(String raw, boolean exception, boolean domainAnchor, String pattern,
             boolean important, int thirdParty, Set<String> includeDomains,
             Set<String> excludeDomains, Set<String> resourceTypes, String redirect) {
            this.raw = raw;
            this.exception = exception;
            this.domainAnchor = domainAnchor;
            this.pattern = pattern;
            this.important = important;
            this.thirdParty = thirdParty;
            this.includeDomains = includeDomains;
            this.excludeDomains = excludeDomains;
            this.resourceTypes = resourceTypes;
            this.redirect = redirect;
        }
    }

    private static final class FilterSet {
        final Set<String> blockHosts = new HashSet<>();
        final Set<String> allowHosts = new HashSet<>();
        final List<Rule> blockRules = new ArrayList<>();
        final List<Rule> allowRules = new ArrayList<>();
        final List<String> genericHideSelectors = new ArrayList<>();
        final Set<String> genericHideClasses = new HashSet<>();
        final Set<String> genericHideIds = new HashSet<>();
        final Map<String, List<String>> domainHideSelectors = new HashMap<>();
        final Map<String, Set<String>> domainUnhideSelectors = new HashMap<>();
        final Set<String> genericHideDisableDomains = new HashSet<>();
    }

    private static final class Engine {
        final FilterSet filters;
        final Map<String, String> resources = new HashMap<>();

        Engine(FilterSet filters) {
            this.filters = filters;
        }
    }

    public static long createFilterSet(String[] filters) {
        FilterSet set = new FilterSet();
        if (filters != null) {
            for (String line : filters) {
                parseLine(set, line);
            }
        }
        long id = nextHandle.getAndIncrement();
        filterSets.put(id, set);
        return id;
    }

    public static FilterListMetadata addFilters(long filterSetPtr, String filePath) {
        FilterSet set = filterSets.get(filterSetPtr);
        if (set == null || TextUtils.isEmpty(filePath)) {
            return null;
        }
        File file = new File(filePath);
        if (!file.isFile()) {
            return null;
        }
        String homepage = null;
        String title = null;
        String redirect = null;
        int expiresSeconds = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (lineNum <= 30 && line.startsWith("!")) {
                    String lower = line.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("! title:")) {
                        title = line.substring(8).trim();
                    } else if (lower.startsWith("! homepage:")) {
                        homepage = line.substring(11).trim();
                    } else if (lower.startsWith("! redirect:")) {
                        redirect = line.substring(11).trim();
                    } else if (lower.startsWith("! expires:")) {
                        expiresSeconds = parseExpires(line.substring(10).trim());
                    }
                    continue;
                }
                parseLine(set, line);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return new FilterListMetadata(homepage, title, redirect, expiresSeconds);
    }

    private static int parseExpires(String value) {
        String lower = value.toLowerCase(Locale.ROOT).trim();
        int space = lower.indexOf(' ');
        String numPart = space > 0 ? lower.substring(0, space) : lower;
        try {
            int amount = Integer.parseInt(numPart.replaceAll("[^0-9]", ""));
            if (lower.contains("hour")) {
                return amount * 3600;
            }
            return amount * 86400;
        } catch (Throwable ignore) {
            return 0;
        }
    }

    public static void destroyFilterSet(long filterSetPtr) {
        filterSets.remove(filterSetPtr);
    }

    public static void useResources(long enginePtr, String[] names, String[][] aliases, String[] extensions, String[] contents) {
        Engine engine = engines.get(enginePtr);
        if (engine == null || names == null || contents == null) {
            return;
        }
        synchronized (engine.resources) {
            for (int i = 0; i < names.length && i < contents.length; i++) {
                String content = contents[i];
                if (TextUtils.isEmpty(names[i]) || content == null) {
                    continue;
                }
                engine.resources.put(names[i], content);
                if (aliases != null && i < aliases.length && aliases[i] != null) {
                    for (String alias : aliases[i]) {
                        if (!TextUtils.isEmpty(alias)) {
                            engine.resources.put(alias, content);
                        }
                    }
                }
            }
        }
    }

    public static long createEngine(long filterSetPtr) {
        FilterSet set = filterSets.remove(filterSetPtr);
        if (set == null) {
            set = new FilterSet();
        }
        long id = nextHandle.getAndIncrement();
        engines.put(id, new Engine(set));
        return id;
    }

    public static void destroyEngine(long enginePtr) {
        engines.remove(enginePtr);
    }

    public static UrlCosmeticResources getCosmeticResources(long enginePtr, String url) {
        Engine engine = engines.get(enginePtr);
        if (engine == null || TextUtils.isEmpty(url)) {
            return null;
        }
        String host = extractHost(url);
        if (TextUtils.isEmpty(host)) {
            return null;
        }
        boolean genericHide = matchesHostSet(engine.filters.genericHideDisableDomains, host);
        LinkedHashSet<String> selectors = new LinkedHashSet<>();
        LinkedHashSet<String> exceptions = new LinkedHashSet<>();

        List<String> hostVariants = hostHierarchy(host);
        for (String h : hostVariants) {
            Set<String> unhide = engine.filters.domainUnhideSelectors.get(h);
            if (unhide != null) {
                exceptions.addAll(unhide);
            }
        }
        for (String h : hostVariants) {
            List<String> domSel = engine.filters.domainHideSelectors.get(h);
            if (domSel != null) {
                for (String sel : domSel) {
                    if (!exceptions.contains(sel)) {
                        selectors.add(sel);
                    }
                }
            }
        }
        return new UrlCosmeticResources(
                selectors.toArray(new String[0]),
                new String[0],
                exceptions.toArray(new String[0]),
                "",
                genericHide
        );
    }

    public static String[] getHiddenSelectors(long enginePtr, String[] classes, String[] ids, String[] exceptions) {
        Engine engine = engines.get(enginePtr);
        if (engine == null) {
            return new String[0];
        }
        Set<String> exceptionSet = Collections.emptySet();
        if (exceptions != null && exceptions.length > 0) {
            exceptionSet = new HashSet<>();
            Collections.addAll(exceptionSet, exceptions);
        }
        ArrayList<String> found = new ArrayList<>();
        if (classes != null) {
            for (String cls : classes) {
                if (TextUtils.isEmpty(cls)) continue;
                if (engine.filters.genericHideClasses.contains(cls)) {
                    String sel = "." + cls;
                    if (!exceptionSet.contains(sel)) {
                        found.add(sel);
                    }
                }
            }
        }
        if (ids != null) {
            for (String id : ids) {
                if (TextUtils.isEmpty(id)) continue;
                if (engine.filters.genericHideIds.contains(id)) {
                    String sel = "#" + id;
                    if (!exceptionSet.contains(sel)) {
                        found.add(sel);
                    }
                }
            }
        }
        return found.toArray(new String[0]);
    }

    public static BlockResult shouldBlock(long enginePtr, String url, String sourceUrl, String resourceType) {
        Engine engine = engines.get(enginePtr);
        if (engine == null || TextUtils.isEmpty(url)) {
            return new BlockResult(false, false, null, null, null, null);
        }
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        String urlHost = extractHost(lowerUrl);
        String srcHost = TextUtils.isEmpty(sourceUrl) ? urlHost : extractHost(sourceUrl.toLowerCase(Locale.ROOT));
        boolean isThirdParty = !TextUtils.isEmpty(urlHost) && !TextUtils.isEmpty(srcHost) && !sameBaseDomain(urlHost, srcHost);
        String type = TextUtils.isEmpty(resourceType) ? "other" : resourceType.toLowerCase(Locale.ROOT);

        // Fast allow host check
        if (!TextUtils.isEmpty(urlHost) && matchesHostSet(engine.filters.allowHosts, urlHost)) {
            return new BlockResult(false, false, null, null, "@@||" + urlHost + "^", null);
        }

        // Check block hosts first (unless overridden below by important=false allow rule)
        boolean hostBlocked = !"main_frame".equals(type) && !TextUtils.isEmpty(urlHost) && matchesHostSet(engine.filters.blockHosts, urlHost);
        Rule matchedBlock = null;
        if (hostBlocked) {
            Rule allow = findMatchingRule(engine.filters.allowRules, lowerUrl, urlHost, srcHost, isThirdParty, type);
            if (allow != null) {
                return new BlockResult(false, false, null, null, allow.raw, null);
            }
            return new BlockResult(true, false, null, null, null, "||" + urlHost + "^");
        }

        for (Rule r : engine.filters.blockRules) {
            if (matchesRule(r, lowerUrl, urlHost, srcHost, isThirdParty, type)) {
                matchedBlock = r;
                break;
            }
        }
        if (matchedBlock == null) {
            return new BlockResult(false, false, null, null, null, null);
        }
        if (!matchedBlock.important) {
            Rule allow = findMatchingRule(engine.filters.allowRules, lowerUrl, urlHost, srcHost, isThirdParty, type);
            if (allow != null) {
                return new BlockResult(false, false, null, null, allow.raw, null);
            }
        }
        return new BlockResult(true, matchedBlock.important, matchedBlock.redirect, null, null, matchedBlock.raw);
    }

    private static Rule findMatchingRule(List<Rule> rules, String lowerUrl, String urlHost, String srcHost, boolean isThirdParty, String type) {
        for (Rule r : rules) {
            if (matchesRule(r, lowerUrl, urlHost, srcHost, isThirdParty, type)) {
                return r;
            }
        }
        return null;
    }

    private static boolean matchesRule(Rule r, String lowerUrl, String urlHost, String srcHost, boolean isThirdParty, String type) {
        if (r.thirdParty == 1 && !isThirdParty) {
            return false;
        }
        if (r.thirdParty == -1 && isThirdParty) {
            return false;
        }
        if (r.resourceTypes != null && !r.resourceTypes.isEmpty() && !r.resourceTypes.contains(type)) {
            return false;
        }
        if (r.excludeDomains != null && !TextUtils.isEmpty(srcHost) && matchesHostSet(r.excludeDomains, srcHost)) {
            return false;
        }
        if (r.includeDomains != null && !r.includeDomains.isEmpty()) {
            if (TextUtils.isEmpty(srcHost) || !matchesHostSet(r.includeDomains, srcHost)) {
                return false;
            }
        }
        if (r.domainAnchor) {
            if (TextUtils.isEmpty(urlHost)) {
                return false;
            }
            if (r.pattern.indexOf('/') < 0) {
                return matchesHost(urlHost, r.pattern);
            }
            int slash = r.pattern.indexOf('/');
            String pHost = r.pattern.substring(0, slash);
            String pPath = r.pattern.substring(slash);
            return matchesHost(urlHost, pHost) && lowerUrl.contains(pPath);
        }
        return lowerUrl.contains(r.pattern);
    }

    private static void parseLine(FilterSet set, String rawLine) {
        if (rawLine == null) {
            return;
        }
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) {
            return;
        }

        // Cosmetic rules
        int cosIdx = line.indexOf("##");
        if (cosIdx >= 0) {
            String domainsPart = line.substring(0, cosIdx).trim();
            String selector = line.substring(cosIdx + 2).trim();
            if (selector.isEmpty() || selector.startsWith("+js(") || selector.contains(":has(") || selector.contains(":xpath(")) {
                return;
            }
            if (domainsPart.isEmpty()) {
                if (selector.startsWith(".") && isSimpleIdent(selector.substring(1))) {
                    set.genericHideClasses.add(selector.substring(1));
                } else if (selector.startsWith("#") && isSimpleIdent(selector.substring(1))) {
                    set.genericHideIds.add(selector.substring(1));
                } else if (set.genericHideSelectors.size() < 2000) {
                    set.genericHideSelectors.add(selector);
                }
            } else {
                for (String dom : domainsPart.split(",")) {
                    String d = dom.trim().toLowerCase(Locale.ROOT);
                    if (d.isEmpty()) continue;
                    if (d.startsWith("~")) {
                        set.domainUnhideSelectors.computeIfAbsent(d.substring(1), k -> new HashSet<>()).add(selector);
                    } else {
                        set.domainHideSelectors.computeIfAbsent(d, k -> new ArrayList<>()).add(selector);
                    }
                }
            }
            return;
        }
        int unhideIdx = line.indexOf("#@#");
        if (unhideIdx >= 0) {
            String domainsPart = line.substring(0, unhideIdx).trim();
            String selector = line.substring(unhideIdx + 3).trim();
            if (!domainsPart.isEmpty() && !selector.isEmpty()) {
                for (String dom : domainsPart.split(",")) {
                    String d = dom.trim().toLowerCase(Locale.ROOT);
                    if (!d.isEmpty()) {
                        set.domainUnhideSelectors.computeIfAbsent(d, k -> new HashSet<>()).add(selector);
                    }
                }
            }
            return;
        }

        // Network rules
        boolean exception = false;
        String work = line;
        if (work.startsWith("@@")) {
            exception = true;
            work = work.substring(2);
        }

        String optionsPart = null;
        int dollarIdx = work.lastIndexOf('$');
        if (dollarIdx > 0 && dollarIdx < work.length() - 1) {
            optionsPart = work.substring(dollarIdx + 1);
            work = work.substring(0, dollarIdx);
        }

        if (work.startsWith("/") && work.endsWith("/") && work.length() > 2) {
            return; // skip pure regex for speed
        }

        boolean important = false;
        int thirdParty = 0;
        boolean genericHideFlag = false;
        Set<String> includeDomains = null;
        Set<String> excludeDomains = null;
        Set<String> types = null;
        String redirect = null;

        if (optionsPart != null) {
            for (String opt : optionsPart.split(",")) {
                String o = opt.trim().toLowerCase(Locale.ROOT);
                if (o.isEmpty()) continue;
                if ("badfilter".equals(o) || "popup".equals(o) || "popunder".equals(o)
                        || "cname".equals(o) || "webrtc".equals(o) || "all".equals(o)
                        || "elemhide".equals(o) || "ehide".equals(o)
                        || "specifichide".equals(o) || "shide".equals(o)
                        || o.startsWith("csp=") || o.startsWith("removeparam=")
                        || o.startsWith("header=") || o.startsWith("replace=")
                        || o.startsWith("rewrite=") || o.startsWith("permissions=")
                        || o.startsWith("uritransform=") || o.startsWith("denyallow=")) {
                    // Неподдерживаемые/специальные модификаторы: пропускаем правило, чтобы не ломать весь сайт
                    return;
                } else if ("important".equals(o)) {
                    important = true;
                } else if ("third-party".equals(o) || "3p".equals(o)) {
                    thirdParty = 1;
                } else if ("~third-party".equals(o) || "1p".equals(o) || "first-party".equals(o)) {
                    thirdParty = -1;
                } else if ("generichide".equals(o) || "ghide".equals(o)) {
                    genericHideFlag = true;
                } else if ("document".equals(o) || "doc".equals(o)) {
                    if (types == null) types = new HashSet<>();
                    types.add("main_frame");
                } else if (o.startsWith("domain=")) {
                    for (String dom : o.substring(7).split("\\|")) {
                        if (dom.isEmpty()) continue;
                        if (dom.startsWith("~")) {
                            if (excludeDomains == null) excludeDomains = new HashSet<>();
                            excludeDomains.add(dom.substring(1));
                        } else {
                            if (includeDomains == null) includeDomains = new HashSet<>();
                            includeDomains.add(dom);
                        }
                    }
                } else if (o.startsWith("redirect=") || o.startsWith("redirect-rule=")) {
                    redirect = o.substring(o.indexOf('=') + 1);
                } else {
                    String mapped = mapOptionType(o);
                    if (mapped != null) {
                        if (types == null) types = new HashSet<>();
                        types.add(mapped);
                    } else {
                        // Неизвестный или инвертированный (~script и т.п.) модификатор: безопасно пропускаем правило
                        return;
                    }
                }
            }
        }

        if (exception && genericHideFlag && work.startsWith("||")) {
            String host = stripHostSeparators(work.substring(2));
            if (!TextUtils.isEmpty(host)) {
                set.genericHideDisableDomains.add(host);
            }
            return;
        }
        if (genericHideFlag) {
            return;
        }

        boolean domainAnchor = false;
        if (work.startsWith("||")) {
            domainAnchor = true;
            work = work.substring(2);
        }
        work = work.toLowerCase(Locale.ROOT);
        while (work.endsWith("^") || work.endsWith("*")) {
            work = work.substring(0, work.length() - 1);
        }
        while (work.startsWith("*")) {
            work = work.substring(1);
        }
        if (work.length() < (domainAnchor ? 4 : 6)) {
            return;
        }

        // Fast pure-host rule path (`||domain.com^` with no options)
        if (domainAnchor && optionsPart == null && work.indexOf('/') < 0 && work.indexOf('*') < 0 && work.indexOf('^') < 0) {
            if (exception) {
                set.allowHosts.add(work);
            } else {
                set.blockHosts.add(work);
            }
            return;
        }

        String pattern = work.replace("^", "/");
        if (pattern.contains("*")) {
            int idx = pattern.indexOf('*');
            pattern = pattern.substring(0, idx);
            if (pattern.length() < (domainAnchor ? 4 : 6)) {
                return;
            }
        }

        Rule rule = new Rule(rawLine, exception, domainAnchor, pattern, important, thirdParty,
                includeDomains, excludeDomains, types, redirect);
        if (exception) {
            set.allowRules.add(rule);
        } else {
            set.blockRules.add(rule);
        }
    }

    private static String mapOptionType(String opt) {
        switch (opt) {
            case "script":
                return "script";
            case "image":
                return "image";
            case "stylesheet":
            case "css":
                return "stylesheet";
            case "xmlhttprequest":
            case "xhr":
                return "xhr";
            case "subdocument":
            case "sub_frame":
                return "sub_frame";
            case "font":
                return "font";
            case "media":
                return "media";
            case "ping":
            case "beacon":
                return "beacon";
            case "websocket":
                return "websocket";
            default:
                return null;
        }
    }

    private static boolean isSimpleIdent(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_')) {
                return false;
            }
        }
        return true;
    }

    private static String stripHostSeparators(String s) {
        String out = s.toLowerCase(Locale.ROOT);
        int cut = out.indexOf('^');
        if (cut >= 0) out = out.substring(0, cut);
        cut = out.indexOf('/');
        if (cut >= 0) out = out.substring(0, cut);
        return out;
    }

    private static String extractHost(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Throwable ignore) {
            int scheme = url.indexOf("://");
            if (scheme < 0) return "";
            String rest = url.substring(scheme + 3);
            int end = rest.indexOf('/');
            if (end >= 0) rest = rest.substring(0, end);
            int port = rest.indexOf(':');
            if (port >= 0) rest = rest.substring(0, port);
            return rest.toLowerCase(Locale.ROOT);
        }
    }

    private static List<String> hostHierarchy(String host) {
        ArrayList<String> list = new ArrayList<>();
        String current = host;
        while (!current.isEmpty()) {
            list.add(current);
            int dot = current.indexOf('.');
            if (dot < 0 || dot == current.length() - 1) break;
            current = current.substring(dot + 1);
            if (current.indexOf('.') < 0) break;
        }
        return list;
    }

    private static boolean matchesHostSet(Set<String> set, String host) {
        String current = host;
        while (!current.isEmpty()) {
            if (set.contains(current)) {
                return true;
            }
            int dot = current.indexOf('.');
            if (dot < 0 || dot == current.length() - 1) break;
            current = current.substring(dot + 1);
        }
        return false;
    }

    private static boolean matchesHost(String host, String domain) {
        if (host.equals(domain)) {
            return true;
        }
        return host.endsWith("." + domain);
    }

    private static boolean sameBaseDomain(String a, String b) {
        if (a.equals(b)) return true;
        String baseA = baseDomain(a);
        String baseB = baseDomain(b);
        return baseA.equals(baseB);
    }

    private static String baseDomain(String host) {
        int last = host.lastIndexOf('.');
        if (last <= 0) return host;
        int prev = host.lastIndexOf('.', last - 1);
        return prev < 0 ? host : host.substring(prev + 1);
    }
}
