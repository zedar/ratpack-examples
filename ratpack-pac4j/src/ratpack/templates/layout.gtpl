yieldUnescaped '<!DOCTYPE html>'
html(lang:'en') {
    head {
        meta(charset:'utf-8')
        title(title ?: 'Pac4j Test with Cookie Session')
        meta('http-equiv': "Content-Type", content:"text/html; charset=utf-8")
        meta(name: 'viewport', content: 'width=device-width, initial-scale=1.0')
        script(src: '/scripts/jquery.min.js') {}
        script(src: '/scripts/bootstrap.min.js') {}
        link(href: '/styles/bootstrap.min.css', rel: 'stylesheet')
        link(href: '/styles/bootstrap-theme.min.css', rel: 'stylesheet')
        link(href: '/styles/example-books.css', rel: 'stylesheet')
        link(href: '/images/favicon.ico', rel: 'shortcut icon')
    }
    body {
        div(class:'container') {
            if (msg) {
                div(class: 'alert alert-info alert-dismissable') {
                    button(type: 'button', class: 'close', 'data-dismiss': 'alert', 'aria-hidden':'true', '&times;')
                    yield msg
                }
            }
            bodyContents()
        }
    }
}
