{{-- MDUI 风格分页器，对齐 PHP Blade 的 layouts/mdui/pageinator.blade.php --}}
{{-- 可用变量：$paginator（分页器对象）、$elements（页码元素列表） --}}
{{-- 每个 element：type = page|separator，page 页码，url 链接，active 是否当前页 --}}
@if ($paginator->hasPages())
    <div class="mdui-row mdui-hidden-md-up">
        <div class="mdui-col-xs-6">
            {{-- 上一页 --}}
            @if ($paginator->onFirstPage())
                <a disabled
                   class="mdui-btn mdui-btn-block mdui-color-theme-accent mdui-btn-raised mdui-ripple">&laquo;</a>
            @else
                <a href="{{ $paginator->previousPageUrl() }}"
                   class="mdui-btn mdui-btn-block mdui-color-theme-accent mdui-btn-raised mdui-ripple">&laquo;</a>
            @endif
        </div>
        <div class="mdui-col-xs-6">
            {{-- 下一页 --}}
            @if ($paginator->hasMorePages())
                <a href="{{ $paginator->nextPageUrl() }}"
                   class="mdui-btn mdui-btn-block mdui-color-theme-accent mdui-btn-raised mdui-ripple">&raquo;</a>
            @else
                <a disabled
                   class="mdui-btn mdui-btn-block mdui-color-theme-accent mdui-btn-raised mdui-ripple">&raquo;</a>
            @endif
        </div>
        <div class="mdui-col-xs-12 mdui-m-l-1 mdui-m-r-1 mdui-text-center">
            @foreach ($elements as $element)
                @if ($element['type'] == 'separator')
                    <a disabled
                       class="mdui-btn mdui-btn-dense mdui-btn-icon mdui-color-theme-accent mdui-ripple">...</a>
                @else
                    @if ($element['active'])
                        <a href="{{ $element['url'] }}"
                           class="mdui-btn mdui-btn-dense mdui-btn-icon mdui-color-theme-accent mdui-ripple">{{ $element['page'] }}</a>
                    @else
                        <a href="{{ $element['url'] }}"
                           class="mdui-btn mdui-btn-dense mdui-btn-icon mdui-ripple">{{ $element['page'] }}</a>
                    @endif
                @endif
            @endforeach
        </div>
    </div>

    <div class="mdui-row mdui-hidden-sm-down">
        <div class="mdui-col-xs-2">
            @if ($paginator->onFirstPage())
                <a disabled
                   class="mdui-btn mdui-btn-block mdui-color-theme-accent mdui-btn-raised mdui-ripple">&laquo;</a>
            @else
                <a href="{{ $paginator->previousPageUrl() }}"
                   class="mdui-btn mdui-btn-block mdui-color-theme-accent mdui-btn-raised mdui-ripple">&laquo;</a>
            @endif
        </div>
        <div class="mdui-col-xs-8 mdui-text-center">
            @foreach ($elements as $element)
                @if ($element['type'] == 'separator')
                    <a disabled
                       class="mdui-btn mdui-btn-icon mdui-btn-dense mdui-color-theme-accent mdui-ripple">...</a>
                @else
                    @if ($element['active'])
                        <a href="{{ $element['url'] }}"
                           class="mdui-btn mdui-btn-dense mdui-btn-icon mdui-color-theme-accent mdui-ripple">{{ $element['page'] }}</a>
                    @else
                        <a href="{{ $element['url'] }}"
                           class="mdui-btn mdui-btn-dense mdui-btn-icon mdui-ripple">{{ $element['page'] }}</a>
                    @endif
                @endif
            @endforeach
        </div>
        <div class="mdui-col-xs-2">
            @if ($paginator->hasMorePages())
                <a href="{{ $paginator->nextPageUrl() }}"
                   class="mdui-btn mdui-btn-block mdui-color-theme-accent mdui-btn-raised mdui-ripple">&raquo;</a>
            @else
                <a disabled
                   class="mdui-btn mdui-btn-block mdui-color-theme-accent mdui-btn-raised mdui-ripple">&raquo;</a>
            @endif
        </div>
    </div>
@endif
